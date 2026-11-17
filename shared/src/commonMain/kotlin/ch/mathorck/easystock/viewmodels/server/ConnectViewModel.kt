package ch.mathorck.easystock.viewmodels.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dto.server.GuestSignupRequest
import ch.mathorck.easystock.data.dto.server.LoginRequest
import ch.mathorck.easystock.data.dto.server.RedeemInviteRequest
import ch.mathorck.easystock.data.dto.server.ResetPasswordRequest
import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.server.SetupRequest
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.enums.server.ConnectError
import ch.mathorck.easystock.enums.server.JoinKind
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.server.AccountRules
import ch.mathorck.easystock.services.server.JoinApi
import ch.mathorck.easystock.services.server.JoinCode
import ch.mathorck.easystock.services.server.ServerConnection
import ch.mathorck.easystock.services.server.TypedAddress
import ch.mathorck.easystock.services.server.checkServerInfo
import ch.mathorck.easystock.services.server.connectErrorOf
import ch.mathorck.easystock.services.server.isLocalNetworkAddress
import ch.mathorck.easystock.services.server.parseTypedAddress
import ch.mathorck.easystock.services.server.withLocalNetwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields of each form. */
fun FormMode.fields(): Set<FormField> = when (this) {
    FormMode.LOGIN -> setOf(FormField.USERNAME, FormField.PASSWORD)
    FormMode.INVITE, FormMode.SETUP -> setOf(FormField.USERNAME, FormField.DISPLAY_NAME, FormField.PASSWORD)
    FormMode.RESET -> setOf(FormField.PASSWORD)
    FormMode.GUEST -> setOf(FormField.DISPLAY_NAME)
}

/**
 * Settings › Server › Connect: from a scanned or pasted join code, or a typed address, to a joined server.
 *
 * Order (docs/server-mode/02-app.md): `/server-info` (is it an EasyStock server this app can talk to),
 * the HTTP warning, the empty-phone check (a one-time code is never spent on a phone that can't join),
 * the form, the join call, then [commit] writes the session. Only the user starts a network call.
 */
class ConnectViewModel(
    /** A client without token for a connection (pinned to its fingerprint). */
    private val joinApi: (ServerConnection) -> JoinApi,
    private val isPhoneEmpty: suspend () -> Boolean,
    /** Erases all data and photos (the reset of App info). */
    private val erase: suspend () -> Unit,
    /** Writes the session; false when the phone is not empty any more. */
    private val commit: suspend (ServerConnection, TokenAnswer) -> Boolean,
    private val deviceName: String,
    private val appVersion: String,
    /** True while the system blocks the local network for this app (Android 17+, see LocalNetworkAccess). */
    private val localNetworkMissing: () -> Boolean = { false },
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var api: JoinApi? = null
    private var apiConnection: ServerConnection? = null

    /** The local network permission was asked in this attempt: once only, a second denial is final on Android 11+. */
    private var localNetworkAsked = false

    /** The server the missing local network permission blocked: tried again once the user allows it. */
    private var deniedTarget: ConnectTarget? = null

    private fun go(step: ConnectStep) = _state.update { it.copy(step = step) }

    private fun fail(error: ConnectError, step: ConnectStep = ConnectStep.Choose) =
        _state.update { it.copy(step = step, error = error) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun apiFor(connection: ServerConnection): JoinApi {
        api?.takeIf { apiConnection == connection }?.let { return it }
        closeApi()
        return joinApi(connection).also {
            api = it
            apiConnection = connection
        }
    }

    private fun closeApi() {
        (api as? AutoCloseable)?.close()
        api = null
        apiConnection = null
    }

    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch { block() }
    }

    // ---------- Choose ----------

    /** Back to the start: drops the code, the fields and the client. */
    fun cancel() {
        job?.cancel()
        closeApi()
        newAttempt()
        _state.value = ConnectUiState(address = _state.value.address)
    }

    fun startScan() = go(ConnectStep.Scanning)

    fun scanCancelled() = go(ConnectStep.Choose)

    /** A QR code read by the camera: only a join code (either format) is taken. */
    fun scanned(text: String) {
        if (_state.value.step != ConnectStep.Scanning) return
        val code = runCatching { JoinCode.parse(text.trim()) }.getOrNull()
        if (code == null) fail(ConnectError.CODE_INVALID) else start(code)
    }

    fun addressChanged(text: String) = _state.update { it.copy(address = text, addressInvalid = false) }

    /** The address field: a typed address (login) or a pasted join link. */
    fun submitAddress() {
        newAttempt()
        when (val typed = parseTypedAddress(_state.value.address)) {
            TypedAddress.Invalid -> _state.update { it.copy(addressInvalid = true) }
            is TypedAddress.Code -> start(typed.code)
            is TypedAddress.Url -> open(ConnectTarget(ServerConnection(typed.url), httpFallback = typed.httpFallback))
        }
    }

    /** A join code, scanned here or in the backup screen. */
    fun start(code: JoinCode) {
        newAttempt()
        _state.update { ConnectUiState(address = it.address) }
        open(ConnectTarget(ServerConnection(code.serverUrl, code.fingerprint), code))
    }

    /**
     * A join code scanned in the backup screen: the user did not tap Connect yet, so it waits for
     * [confirmCode] (HTTP codes wait in the HTTP warning) before any call.
     */
    fun offer(code: JoinCode) {
        newAttempt()
        _state.update { ConnectUiState(address = it.address) }
        val target = ConnectTarget(ServerConnection(code.serverUrl, code.fingerprint), code)
        go(if (target.connection.isHttps) ConnectStep.ConfirmCode(target) else ConnectStep.HttpWarning(target))
    }

    /** The user confirms the code offered by [offer]. */
    fun confirmCode() {
        val step = _state.value.step as? ConnectStep.ConfirmCode ?: return
        check(step.target)
    }

    private fun open(target: ConnectTarget) {
        if (target.connection.isHttps) check(target) else go(ConnectStep.HttpWarning(target))
    }

    // ---------- HTTP ----------

    /** After [ConnectStep.HttpsFailed]: try the plain HTTP address, after the warning. */
    fun tryHttp() {
        val step = _state.value.step as? ConnectStep.HttpsFailed ?: return
        go(ConnectStep.HttpWarning(ConnectTarget(ServerConnection(step.httpUrl), step.target.code)))
    }

    /** The user accepts plain HTTP. */
    fun acceptHttp() {
        val step = _state.value.step as? ConnectStep.HttpWarning ?: return
        check(step.target)
    }

    private fun newAttempt() {
        localNetworkAsked = false
        deniedTarget = null
    }

    // ---------- Local network permission (Android 17+) ----------

    /** The answer of the system dialog of [ConnectStep.LocalNetwork]. */
    fun localNetworkAnswered(granted: Boolean) {
        val step = _state.value.step as? ConnectStep.LocalNetwork ?: return
        // A private address can't answer without it: no need to wait for the connect timeout.
        // Anything else is tried (a public server needs no permission).
        if (!granted && isLocalNetworkAddress(step.target.connection.serverUrl)) {
            localNetworkDenied(step.target)
        } else {
            callServerInfo(step.target)
        }
    }

    private fun localNetworkDenied(target: ConnectTarget) {
        deniedTarget = target
        fail(ConnectError.LOCAL_NETWORK_DENIED)
    }

    /**
     * The user allowed the local network after [ConnectError.LOCAL_NETWORK_DENIED] (from the error dialog or
     * the app settings): the blocked server is tried again; a form stays for the user to send again.
     */
    fun localNetworkGranted() {
        if (_state.value.error != ConnectError.LOCAL_NETWORK_DENIED || localNetworkMissing()) return
        val target = deniedTarget
        deniedTarget = null
        dismissError()
        if (target != null && _state.value.step == ConnectStep.Choose) check(target)
    }

    // ---------- Server info and empty phone ----------

    /** Every way to a server goes through here: the local network permission is asked before the first call. */
    private fun check(target: ConnectTarget) {
        if (!localNetworkAsked && localNetworkMissing()) {
            localNetworkAsked = true
            go(ConnectStep.LocalNetwork(target))
            return
        }
        callServerInfo(target)
    }

    private fun callServerInfo(target: ConnectTarget) {
        go(ConnectStep.Checking(target))
        launch {
            val info = try {
                apiFor(target.connection).serverInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val fallback = target.httpFallback
                when {
                    // A certificate refused: something answered but can't prove it is the server.
                    // Offering plain HTTP then would hand the connection to whoever answered
                    e.tlsFailure -> fail(ConnectError.TLS)
                    // Before the HTTP fallback: plain HTTP would be blocked the same way
                    e.error == ApiError.NETWORK && localNetworkMissing() -> localNetworkDenied(target)
                    e.error == ApiError.NETWORK && fallback != null -> go(ConnectStep.HttpsFailed(target, fallback))
                    e.error == ApiError.NETWORK -> fail(ConnectError.NETWORK)
                    // An answer, but no /server-info: not an EasyStock server
                    else -> fail(ConnectError.NOT_A_SERVER)
                }
                return@launch
            } catch (e: Exception) {
                if (localNetworkMissing()) localNetworkDenied(target) else fail(ConnectError.NETWORK)
                return@launch
            }
            val problem = checkServerInfo(info, appVersion) ?: when {
                target.code?.kind == JoinKind.SETUP && info.setupDone -> ConnectError.SETUP_DONE
                target.code?.kind != JoinKind.SETUP && !info.setupDone -> ConnectError.NOT_SET_UP
                else -> null
            }
            if (problem != null) {
                fail(problem)
                return@launch
            }
            if (runCatching { isPhoneEmpty() }.getOrDefault(false)) {
                showForm(target, info)
            } else {
                go(ConnectStep.NotEmpty(target, info))
            }
        }
    }

    /** From [ConnectStep.NotEmpty]: the typed confirmation of the erase. */
    fun askErase() {
        val step = _state.value.step as? ConnectStep.NotEmpty ?: return
        go(ConnectStep.EraseConfirm(step.target, step.info))
    }

    /** Back from the erase confirmation (e.g. to export a backup first): the choice stays. */
    fun cancelErase() {
        val step = _state.value.step as? ConnectStep.EraseConfirm ?: return
        go(ConnectStep.NotEmpty(step.target, step.info))
    }

    fun confirmErase() {
        val step = _state.value.step as? ConnectStep.EraseConfirm ?: return
        go(ConnectStep.Erasing(step.target, step.info))
        launch {
            val empty = runCatching {
                erase()
                isPhoneEmpty()
            }.getOrDefault(false)
            if (empty) showForm(step.target, step.info) else go(step.copy(failed = true))
        }
    }

    private fun showForm(target: ConnectTarget, info: ServerInfo) {
        val mode = when (target.code?.kind) {
            null -> FormMode.LOGIN
            JoinKind.INVITE -> FormMode.INVITE
            JoinKind.SETUP -> FormMode.SETUP
            JoinKind.RESET -> FormMode.RESET
            JoinKind.GUEST -> {
                go(ConnectStep.GuestChoice(target, info))
                return
            }
        }
        go(ConnectStep.Form(target, info, mode))
    }

    // ---------- Forms ----------

    /** A printed code: log in to an own account on that server. */
    fun chooseLogin() {
        val step = _state.value.step as? ConnectStep.GuestChoice ?: return
        go(ConnectStep.Form(step.target, step.info, FormMode.LOGIN))
    }

    fun chooseGuest() {
        val step = _state.value.step as? ConnectStep.GuestChoice ?: return
        if (!step.info.guestSignupEnabled) return
        go(ConnectStep.Form(step.target, step.info, FormMode.GUEST))
    }

    fun usernameChanged(text: String) = _state.update { it.copy(username = text, invalidFields = it.invalidFields - FormField.USERNAME) }

    fun displayNameChanged(text: String) =
        _state.update { it.copy(displayName = text, invalidFields = it.invalidFields - FormField.DISPLAY_NAME) }

    fun passwordChanged(text: String) = _state.update { it.copy(password = text, invalidFields = it.invalidFields - FormField.PASSWORD) }

    fun submitForm() {
        val step = _state.value.step as? ConnectStep.Form ?: return
        if (step.busy) return
        val s = _state.value
        val username = s.username.trim()
        val displayName = s.displayName.trim()
        val invalid = step.mode.fields().filterTo(mutableSetOf()) { field ->
            when (field) {
                FormField.USERNAME -> !AccountRules.isValidUsername(username)
                FormField.DISPLAY_NAME -> !AccountRules.isValidDisplayName(displayName)
                // A login checks the password only on the server: an old one may be shorter
                FormField.PASSWORD -> if (step.mode == FormMode.LOGIN) s.password.isEmpty() else !AccountRules.isValidPassword(s.password)
            }
        }
        if (invalid.isNotEmpty()) {
            _state.update { it.copy(invalidFields = invalid) }
            return
        }
        val token = step.target.code?.token.orEmpty()
        val api = apiFor(step.target.connection)
        go(step.copy(busy = true))
        launch {
            val answer = try {
                when (step.mode) {
                    FormMode.LOGIN -> api.login(LoginRequest(username, s.password, deviceName))
                    FormMode.INVITE -> api.redeemInvite(RedeemInviteRequest(token, username, displayName, s.password, deviceName))
                    FormMode.SETUP -> api.setup(SetupRequest(token, username, displayName, s.password, deviceName))
                    FormMode.RESET -> api.resetPassword(ResetPasswordRequest(token, s.password, deviceName))
                    FormMode.GUEST -> api.guestSignup(GuestSignupRequest(token, displayName, deviceName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val error = connectErrorOf(e).withLocalNetwork(localNetworkMissing())
                // The code can't be used: back to the start. Otherwise the form stays to fix it.
                fail(error, if (error in CODE_FATAL) ConnectStep.Choose else step)
                return@launch
            } catch (e: Exception) {
                fail(ConnectError.NETWORK.withLocalNetwork(localNetworkMissing()), step)
                return@launch
            }
            val joined = try {
                commit(step.target.connection, answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (joined) {
                closeApi()
                _state.value = ConnectUiState(step = ConnectStep.Done)
            } else {
                fail(ConnectError.LOCAL)
            }
        }
    }

    /** Back from the form or a choice: to the start. */
    fun back() = cancel()

    override fun onCleared() {
        closeApi()
    }

    private companion object {
        val CODE_FATAL = setOf(
            ConnectError.CODE_INVALID,
            ConnectError.CODE_EXPIRED,
            ConnectError.CODE_USED,
            ConnectError.SETUP_DONE,
            ConnectError.GUEST_SIGNUP_OFF,
            ConnectError.GUEST_LIMIT_REACHED,
            ConnectError.APP_TOO_OLD,
            ConnectError.ACCOUNT_DISABLED,
        )
    }
}

package ch.mathorck.easystock.viewmodels.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.server.AccountRules
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ConnectError
import ch.mathorck.easystock.server.GuestSignupRequest
import ch.mathorck.easystock.server.JoinApi
import ch.mathorck.easystock.server.JoinCode
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.LoginRequest
import ch.mathorck.easystock.server.RedeemInviteRequest
import ch.mathorck.easystock.server.ResetPasswordRequest
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.ServerInfo
import ch.mathorck.easystock.server.SetupRequest
import ch.mathorck.easystock.server.TokenAnswer
import ch.mathorck.easystock.server.TypedAddress
import ch.mathorck.easystock.server.checkServerInfo
import ch.mathorck.easystock.server.connectErrorOf
import ch.mathorck.easystock.server.parseTypedAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The form shown for a join code ([JoinKind]) or a typed address ([LOGIN]). */
enum class FormMode { LOGIN, INVITE, SETUP, RESET, GUEST }

/** A server to join, and the code it was reached with (null: a typed address, the user logs in). */
data class ConnectTarget(
    val connection: ServerConnection,
    val code: JoinCode? = null,
    /** A typed address without scheme tried over HTTPS: this plain HTTP address can be offered after a failure. */
    val httpFallback: String? = null,
)

/** Where the connect screen is. Nothing is written on the phone before [Done]. */
sealed interface ConnectStep {
    /** Scan a code or type an address. */
    data object Choose : ConnectStep

    data object Scanning : ConnectStep

    /** Asking `/server-info`. */
    data class Checking(val target: ConnectTarget) : ConnectStep

    /** HTTPS gave no answer: offer plain HTTP ([httpUrl]), after the warning. */
    data class HttpsFailed(val target: ConnectTarget, val httpUrl: String) : ConnectStep

    /** Before the first call over plain HTTP: the user must accept it. */
    data class HttpWarning(val target: ConnectTarget) : ConnectStep

    /** The server answers, but this phone has data: export it and erase it, or cancel. */
    data class NotEmpty(val target: ConnectTarget, val info: ServerInfo) : ConnectStep

    /** The typed confirmation of the erase (with the offer to export first). */
    data class EraseConfirm(val target: ConnectTarget, val info: ServerInfo, val failed: Boolean = false) : ConnectStep

    data class Erasing(val target: ConnectTarget, val info: ServerInfo) : ConnectStep

    /** A printed Guest code: log in to an own account, or continue as a Guest. */
    data class GuestChoice(val target: ConnectTarget, val info: ServerInfo) : ConnectStep

    data class Form(
        val target: ConnectTarget,
        val info: ServerInfo,
        val mode: FormMode,
        /** The join call runs. */
        val busy: Boolean = false,
    ) : ConnectStep

    /** Joined: sync_state and the tokens are written. */
    data object Done : ConnectStep
}

enum class FormField { USERNAME, DISPLAY_NAME, PASSWORD }

data class ConnectUiState(
    val step: ConnectStep = ConnectStep.Choose,
    val address: String = "",
    /** The typed address can't be read. */
    val addressInvalid: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    /** Fields that break the account rules, shown after a submit. */
    val invalidFields: Set<FormField> = emptySet(),
    /** Shown in a dialog. */
    val error: ConnectError? = null,
) {
    // The password must never end up in logs
    override fun toString(): String = "ConnectUiState(step=$step, address=$address, error=$error)"
}

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
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var api: JoinApi? = null
    private var apiConnection: ServerConnection? = null

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
        _state.value = ConnectUiState(address = _state.value.address)
    }

    fun startScan() = go(ConnectStep.Scanning)

    fun scanCancelled() = go(ConnectStep.Choose)

    fun addressChanged(text: String) = _state.update { it.copy(address = text, addressInvalid = false) }

    /** The address field: a typed address (login) or a pasted join link. */
    fun submitAddress() {
        when (val typed = parseTypedAddress(_state.value.address)) {
            TypedAddress.Invalid -> _state.update { it.copy(addressInvalid = true) }
            is TypedAddress.Code -> start(typed.code)
            is TypedAddress.Url -> open(ConnectTarget(ServerConnection(typed.url), httpFallback = typed.httpFallback))
        }
    }

    /** A join code, scanned here or in the backup screen. */
    fun start(code: JoinCode) {
        _state.update { ConnectUiState(address = it.address) }
        open(ConnectTarget(ServerConnection(code.serverUrl, code.fingerprint), code))
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

    // ---------- Server info and empty phone ----------

    private fun check(target: ConnectTarget) {
        go(ConnectStep.Checking(target))
        launch {
            val info = try {
                apiFor(target.connection).serverInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val fallback = target.httpFallback
                when {
                    e.error == ApiError.NETWORK && fallback != null -> go(ConnectStep.HttpsFailed(target, fallback))
                    e.error == ApiError.NETWORK -> fail(ConnectError.NETWORK)
                    // An answer, but no /server-info: not an EasyStock server
                    else -> fail(ConnectError.NOT_A_SERVER)
                }
                return@launch
            } catch (e: Exception) {
                fail(ConnectError.NETWORK)
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
                val error = connectErrorOf(e)
                // The code can't be used: back to the start. Otherwise the form stays to fix it.
                fail(error, if (error in CODE_FATAL) ConnectStep.Choose else step)
                return@launch
            } catch (e: Exception) {
                fail(ConnectError.NETWORK, step)
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

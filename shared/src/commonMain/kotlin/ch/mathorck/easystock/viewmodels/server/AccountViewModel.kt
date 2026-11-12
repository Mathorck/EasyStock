package ch.mathorck.easystock.viewmodels.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.auth.ServerSessionEvents
import ch.mathorck.easystock.server.AccountApi
import ch.mathorck.easystock.server.AccountRules
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.CompletePromotionRequest
import ch.mathorck.easystock.server.ConnectError
import ch.mathorck.easystock.server.MeAnswer
import ch.mathorck.easystock.server.TokenAnswer
import ch.mathorck.easystock.server.connectErrorOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The logout, step by step. */
sealed interface LogoutStep {
    /**
     * [pending]: local changes not sent yet (they are lost). [guest]: the account is deleted.
     * [syncFailed]: "Sync, then log out" could not send them all.
     */
    data class Confirm(val pending: Int, val guest: Boolean, val syncFailed: Boolean = false) : LogoutStep

    /** "Sync, then log out": the changes are being sent. */
    data class Syncing(val guest: Boolean) : LogoutStep

    data object Working : LogoutStep

    /** The server can't be reached: log out on this phone only (the session stays valid on the server until it expires). */
    data object Offline : LogoutStep

    /** The phone is standalone and empty again. */
    data object Done : LogoutStep
}

/** The promotion form: a Guest the Admin promoted chooses a username and a password. */
data class PromotionForm(
    val username: String = "",
    val password: String = "",
    val invalidFields: Set<FormField> = emptySet(),
    val busy: Boolean = false,
) {
    override fun toString(): String = "PromotionForm(username=$username, busy=$busy)"
}

/** The display name dialog. */
data class NameForm(val text: String, val invalid: Boolean = false, val busy: Boolean = false)

/** The change password dialog. [wrongCurrent]: the server refused the current password. */
data class PasswordForm(
    val current: String = "",
    val new: String = "",
    /** Only [FormField.PASSWORD] (the new one). */
    val invalidFields: Set<FormField> = emptySet(),
    val currentEmpty: Boolean = false,
    val wrongCurrent: Boolean = false,
    val busy: Boolean = false,
) {
    override fun toString(): String = "PasswordForm(busy=$busy)"
}

data class AccountUiState(
    /** From `GET /me`, null until it answered. */
    val me: MeAnswer? = null,
    /** `GET /me` failed: the screen shows what is stored on the phone. */
    val meError: ConnectError? = null,
    /** An alerts switch change is being sent. */
    val alertsBusy: Boolean = false,
    val logout: LogoutStep? = null,
    val promotion: PromotionForm? = null,
    val nameForm: NameForm? = null,
    val passwordForm: PasswordForm? = null,
    /** The password was changed: a dialog tells that the other devices must log in again. */
    val passwordChanged: Boolean = false,
    /** Shown once in a dialog. */
    val error: ConnectError? = null,
)

/**
 * Settings › Server when connected: the own account, the promotion of a Guest, the logout.
 * A logout always ends with the wipe ([wipe]: tokens, every row, photos, sync_state), even when the server
 * can't be told, so a phone that leaves a server is empty and standalone.
 */
class AccountViewModel(
    private val api: AccountApi,
    private val pendingChanges: suspend () -> Int,
    /** Runs a sync cycle, then answers the local changes still not sent. */
    private val syncFirst: suspend () -> Int,
    /** Stores the role and display name `GET /me` answered. */
    private val onMe: suspend (MeAnswer) -> Unit,
    /** Saves the tokens of a completed promotion and its new role. */
    private val onPromoted: suspend (TokenAnswer) -> Unit,
    /** The reset of all data, which also clears the session. */
    private val wipe: suspend () -> Unit,
    /** Runs first in a logout, while the session works: the server forgets the push address. Best effort. */
    private val beforeLogout: suspend () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissPasswordChanged() = _state.update { it.copy(passwordChanged = false) }

    fun refresh() {
        viewModelScope.launch {
            try {
                val me = api.me()
                _state.update { it.copy(me = me, meError = null) }
                runCatching { onMe(me) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // A revoked session is handled by MainComposable (the client reported it)
                _state.update { it.copy(meError = connectErrorOf(e)) }
            } catch (e: Exception) {
                _state.update { it.copy(meError = ConnectError.NETWORK) }
            }
        }
    }

    // ---------- Alerts ----------

    /**
     * Turns the low-stock alerts on or off (`PATCH /me`). The switch changes at once; a failure puts it back
     * and shows the error. [onSaved] runs after the server accepted it (the screen asks the notification
     * permission there when turned on).
     */
    fun setAlerts(enabled: Boolean, onSaved: () -> Unit = {}) {
        val before = _state.value
        val me = before.me ?: return
        if (before.alertsBusy) return
        _state.update { it.copy(me = me.copy(alertsEnabled = enabled), alertsBusy = true) }
        viewModelScope.launch {
            try {
                api.setAlertsEnabled(enabled)
                _state.update { it.copy(alertsBusy = false) }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? ApiException)?.let(::connectErrorOf) ?: ConnectError.NETWORK
                _state.update { it.copy(me = it.me?.copy(alertsEnabled = me.alertsEnabled), alertsBusy = false, error = error) }
            }
        }
    }

    // ---------- Display name ----------

    fun openName() = _state.update { it.copy(nameForm = NameForm(it.me?.displayName.orEmpty())) }

    fun closeName() = _state.update { if (it.nameForm?.busy == true) it else it.copy(nameForm = null) }

    fun nameChanged(text: String) = _state.update { it.copy(nameForm = it.nameForm?.copy(text = text, invalid = false)) }

    /** `PATCH /me`: the answer replaces the account shown and is stored like a `GET /me`. */
    fun submitName() {
        val form = _state.value.nameForm ?: return
        if (form.busy) return
        val name = form.text.trim()
        if (!AccountRules.isValidDisplayName(name)) {
            _state.update { it.copy(nameForm = form.copy(invalid = true)) }
            return
        }
        _state.update { it.copy(nameForm = form.copy(busy = true)) }
        viewModelScope.launch {
            try {
                val me = api.setDisplayName(name)
                _state.update { it.copy(me = me, meError = null, nameForm = null) }
                runCatching { onMe(me) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? ApiException)?.let(::connectErrorOf) ?: ConnectError.NETWORK
                _state.update { it.copy(nameForm = form.copy(busy = false), error = error) }
            }
        }
    }

    // ---------- Password ----------

    fun openPassword() = _state.update { it.copy(passwordForm = PasswordForm()) }

    fun closePassword() = _state.update { if (it.passwordForm?.busy == true) it else it.copy(passwordForm = null) }

    fun currentPasswordChanged(text: String) = _state.update {
        it.copy(
            passwordForm = it.passwordForm?.copy(
                current = text,
                wrongCurrent = false,
                currentEmpty = false,
            ),
        )
    }

    fun newPasswordChanged(text: String) = _state.update {
        it.copy(passwordForm = it.passwordForm?.copy(new = text, invalidFields = it.passwordForm.invalidFields - FormField.PASSWORD))
    }

    /**
     * `POST /me/password`. A wrong current password stays in the form as a field error; other failures show
     * the error dialog and keep the form. The other sessions end on the server, this one stays valid.
     */
    fun submitPassword() {
        val form = _state.value.passwordForm ?: return
        if (form.busy) return
        val currentEmpty = form.current.isEmpty()
        val invalid = if (AccountRules.isValidPassword(form.new)) emptySet() else setOf(FormField.PASSWORD)
        if (currentEmpty || invalid.isNotEmpty()) {
            _state.update { it.copy(passwordForm = form.copy(invalidFields = invalid, currentEmpty = currentEmpty)) }
            return
        }
        _state.update { it.copy(passwordForm = form.copy(busy = true)) }
        viewModelScope.launch {
            try {
                api.changePassword(form.current, form.new)
                _state.update { it.copy(passwordForm = null, passwordChanged = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? ApiException)?.let(::connectErrorOf) ?: ConnectError.NETWORK
                if (error == ConnectError.WRONG_CREDENTIALS) {
                    _state.update { it.copy(passwordForm = form.copy(busy = false, wrongCurrent = true)) }
                } else {
                    _state.update { it.copy(passwordForm = form.copy(busy = false), error = error) }
                }
            }
        }
    }

    // ---------- Logout ----------

    fun askLogout(guest: Boolean) {
        viewModelScope.launch {
            val pending = runCatching { pendingChanges() }.getOrDefault(0)
            _state.update { it.copy(logout = LogoutStep.Confirm(pending, guest)) }
        }
    }

    fun cancelLogout() = _state.update {
        if (it.logout == LogoutStep.Working || it.logout is LogoutStep.Syncing) it else it.copy(logout = null)
    }

    /** Tells the server, then wipes the phone. */
    fun confirmLogout() {
        if (_state.value.logout !is LogoutStep.Confirm) return
        logoutNow()
    }

    /** Sends the local changes first; logs out only when none is left, else asks again. */
    fun syncThenLogout() {
        val step = _state.value.logout
        if (step !is LogoutStep.Confirm) return
        _state.update { it.copy(logout = LogoutStep.Syncing(step.guest)) }
        viewModelScope.launch {
            val left = try {
                syncFirst()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (left == 0) {
                logoutNow()
            } else {
                _state.update { it.copy(logout = LogoutStep.Confirm(left ?: step.pending, step.guest, syncFailed = true)) }
            }
        }
    }

    private fun logoutNow() {
        _state.update { it.copy(logout = LogoutStep.Working) }
        viewModelScope.launch {
            ServerSessionEvents.leaving = true
            try {
                beforeLogout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The logout goes on: the server drops the address with the session
            }
            val told = try {
                api.logout()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Already revoked on the server: nothing to tell, wipe anyway
                e.error == ApiError.SESSION_REVOKED
            } catch (e: Exception) {
                false
            }
            if (told) finishLogout() else _state.update { it.copy(logout = LogoutStep.Offline) }
        }
    }

    /** After [LogoutStep.Offline]: wipe without telling the server. */
    fun logoutOffline() {
        if (_state.value.logout != LogoutStep.Offline) return
        _state.update { it.copy(logout = LogoutStep.Working) }
        viewModelScope.launch { finishLogout() }
    }

    /** [LogoutStep.Offline] closed: stays connected. */
    fun stayConnected() {
        if (_state.value.logout != LogoutStep.Offline) return
        ServerSessionEvents.leaving = false
        _state.update { it.copy(logout = null) }
    }

    private suspend fun finishLogout() {
        try {
            wipe()
            _state.update { it.copy(logout = LogoutStep.Done) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(logout = null, error = ConnectError.LOCAL) }
        } finally {
            ServerSessionEvents.leaving = false
        }
    }

    // ---------- Promotion ----------

    fun openPromotion() = _state.update { it.copy(promotion = PromotionForm()) }

    fun closePromotion() = _state.update { if (it.promotion?.busy == true) it else it.copy(promotion = null) }

    fun promotionUsernameChanged(text: String) = _state.update {
        it.copy(promotion = it.promotion?.let { f -> f.copy(username = text, invalidFields = f.invalidFields - FormField.USERNAME) })
    }

    fun promotionPasswordChanged(text: String) = _state.update {
        it.copy(promotion = it.promotion?.let { f -> f.copy(password = text, invalidFields = f.invalidFields - FormField.PASSWORD) })
    }

    fun submitPromotion() {
        val form = _state.value.promotion ?: return
        if (form.busy) return
        val username = form.username.trim()
        val invalid = buildSet {
            if (!AccountRules.isValidUsername(username)) add(FormField.USERNAME)
            if (!AccountRules.isValidPassword(form.password)) add(FormField.PASSWORD)
        }
        if (invalid.isNotEmpty()) {
            _state.update { it.copy(promotion = form.copy(invalidFields = invalid)) }
            return
        }
        _state.update { it.copy(promotion = form.copy(busy = true)) }
        viewModelScope.launch {
            try {
                val answer = api.completePromotion(CompletePromotionRequest(username, form.password))
                onPromoted(answer)
                _state.update { it.copy(promotion = null) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? ApiException)?.let(::connectErrorOf) ?: ConnectError.NETWORK
                // An expired or withdrawn promotion can't be retried: the form closes
                val keep = error !in setOf(ConnectError.CODE_EXPIRED, ConnectError.NO_PROMOTION, ConnectError.SESSION_REVOKED)
                _state.update { it.copy(promotion = if (keep) form.copy(busy = false) else null, error = error) }
                if (!keep) refresh()
            }
        }
    }
}

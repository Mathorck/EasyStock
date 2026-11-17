package ch.mathorck.easystock.viewmodels.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dto.server.CompletePromotionRequest
import ch.mathorck.easystock.data.dto.server.MeAnswer
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.enums.server.ConnectError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.auth.ServerSessionEvents
import ch.mathorck.easystock.services.server.AccountApi
import ch.mathorck.easystock.services.server.AccountRules
import ch.mathorck.easystock.services.server.connectErrorOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /**
     * The wipe after the server deleted the account, like [wipe]. [backupUnsent]: the user chose to delete with
     * changes not sent: they are saved in an automatic backup first, which the wipe keeps.
     */
    private val wipeAfterDelete: suspend (backupUnsent: Boolean) -> Unit = { wipe() },
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
            } finally {
                // Also when cancelled (screen closed) or offline: until the user confirms, the session
                // is kept, and a revoke by the server must be reported again
                ServerSessionEvents.leaving = false
            }
        }
    }

    /** After [LogoutStep.Offline]: wipe without telling the server. */
    fun logoutOffline() {
        if (_state.value.logout != LogoutStep.Offline) return
        _state.update { it.copy(logout = LogoutStep.Working) }
        viewModelScope.launch {
            ServerSessionEvents.leaving = true
            finishLogout()
        }
    }

    /** [LogoutStep.Offline] closed: stays connected. */
    fun stayConnected() {
        if (_state.value.logout != LogoutStep.Offline) return
        ServerSessionEvents.leaving = false
        _state.update { it.copy(logout = null) }
    }

    /** The screen is gone: a logout that did not finish must not hide a later revoke. */
    override fun onCleared() {
        ServerSessionEvents.leaving = false
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

    // ---------- Delete my account ----------

    fun askDelete(guest: Boolean) = _state.update { it.copy(deleteForm = DeleteForm(guest)) }

    fun cancelDelete() = _state.update { if (it.deleteForm?.busy == true) it else it.copy(deleteForm = null) }

    fun deletePasswordChanged(text: String) = _state.update {
        it.copy(deleteForm = it.deleteForm?.copy(password = text, passwordEmpty = false, wrongPassword = false))
    }

    /**
     * Confirmed: sends the local changes first (they would be lost with the account's session), then deletes.
     * When not all of them could be sent, the user chooses: [deleteAnyway] or [cancelDelete].
     */
    fun confirmDelete() {
        val form = _state.value.deleteForm ?: return
        if (form.phase != DeletePhase.CONFIRM) return
        if (!form.guest && form.password.isEmpty()) {
            _state.update { it.copy(deleteForm = form.copy(passwordEmpty = true)) }
            return
        }
        _state.update { it.copy(deleteForm = form.copy(phase = DeletePhase.SYNCING)) }
        viewModelScope.launch {
            val pending = try {
                pendingChanges()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val left = if (pending == 0) {
                0
            } else {
                try {
                    syncFirst()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (left == 0) {
                deleteNow(form, backupUnsent = false)
            } else {
                _state.update { it.copy(deleteForm = form.copy(phase = DeletePhase.SYNC_FAILED, pending = left ?: pending)) }
            }
        }
    }

    /** After [DeletePhase.SYNC_FAILED]: deletes; the unsent changes are saved in an automatic backup first. */
    fun deleteAnyway() {
        val form = _state.value.deleteForm ?: return
        if (form.phase != DeletePhase.SYNC_FAILED) return
        deleteNow(form, backupUnsent = true)
    }

    private fun deleteNow(form: DeleteForm, backupUnsent: Boolean) {
        _state.update { it.copy(deleteForm = form.copy(phase = DeletePhase.DELETING)) }
        viewModelScope.launch {
            // The session ends with the account: a 401 of another call must not start a second wipe
            ServerSessionEvents.leaving = true
            var deleted = false
            var error: ConnectError? = null
            try {
                api.deleteAccount(if (form.guest) null else form.password)
                deleted = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e as? ApiException)?.let(::connectErrorOf) ?: ConnectError.NETWORK
            } finally {
                // Until it is deleted, the account and the phone stay as they are
                if (!deleted) ServerSessionEvents.leaving = false
            }
            if (deleted) finishDelete(backupUnsent) else deleteFailed(form, error ?: ConnectError.NETWORK)
        }
    }

    /** The server refused or could not be reached: nothing changed, on the server or on the phone. */
    private fun deleteFailed(form: DeleteForm, error: ConnectError) {
        val back = form.copy(phase = DeletePhase.CONFIRM, pending = null)
        when (error) {
            ConnectError.WRONG_CREDENTIALS -> _state.update { it.copy(deleteForm = back.copy(wrongPassword = true)) }
            // Can't be retried as it is
            ConnectError.LAST_ADMIN -> _state.update { it.copy(deleteForm = null, error = error) }
            ConnectError.SESSION_REVOKED -> {
                // The client already dropped the tokens, but leaving hid the revoke: report it now (leaving is off)
                _state.update { it.copy(deleteForm = null) }
                ServerSessionEvents.sessionRevoked()
            }
            else -> _state.update { it.copy(deleteForm = back, error = error) }
        }
    }

    private suspend fun finishDelete(backupUnsent: Boolean) {
        try {
            wipeAfterDelete(backupUnsent)
            _state.update { it.copy(deleteForm = it.deleteForm?.copy(phase = DeletePhase.DONE)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(deleteForm = null) }
            ServerSessionEvents.leaving = false
            // The account is gone, the session with it: the revoke handler wipes again (and offers to retry)
            ServerSessionEvents.sessionRevoked()
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

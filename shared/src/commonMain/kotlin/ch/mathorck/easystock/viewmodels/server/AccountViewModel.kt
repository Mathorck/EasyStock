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
    /** [pending]: local changes not sent yet (they are lost). [guest]: the account is deleted. */
    data class Confirm(val pending: Int, val guest: Boolean) : LogoutStep

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

data class AccountUiState(
    /** From `GET /me`, null until it answered. */
    val me: MeAnswer? = null,
    /** `GET /me` failed: the screen shows what is stored on the phone. */
    val meError: ConnectError? = null,
    val logout: LogoutStep? = null,
    val promotion: PromotionForm? = null,
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
    /** Stores the role and display name `GET /me` answered. */
    private val onMe: suspend (MeAnswer) -> Unit,
    /** Saves the tokens of a completed promotion and its new role. */
    private val onPromoted: suspend (TokenAnswer) -> Unit,
    /** The reset of all data, which also clears the session. */
    private val wipe: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    fun dismissError() = _state.update { it.copy(error = null) }

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

    // ---------- Logout ----------

    fun askLogout(guest: Boolean) {
        viewModelScope.launch {
            val pending = runCatching { pendingChanges() }.getOrDefault(0)
            _state.update { it.copy(logout = LogoutStep.Confirm(pending, guest)) }
        }
    }

    fun cancelLogout() = _state.update { if (it.logout == LogoutStep.Working) it else it.copy(logout = null) }

    /** Tells the server, then wipes the phone. */
    fun confirmLogout() {
        val step = _state.value.logout
        if (step !is LogoutStep.Confirm) return
        _state.update { it.copy(logout = LogoutStep.Working) }
        viewModelScope.launch {
            ServerSessionEvents.leaving = true
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

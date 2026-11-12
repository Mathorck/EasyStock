package ch.mathorck.easystock.viewmodels.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.server.AccountRules
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminUser
import ch.mathorck.easystock.server.QrGrant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Short code of a Guest without what people add or change when they read it: case, spaces, dashes.
 * "k7-4qx", "K7 4QX" and "K74QX" are the same code.
 */
fun normalizeShortCode(code: String): String = code.filter { it.isLetterOrDigit() }.uppercase()

/** True when the code the Guest shows ([typed]) is the code of the account ([expected]). */
fun shortCodesMatch(expected: String?, typed: String): Boolean {
    val want = normalizeShortCode(expected ?: return false)
    return want.isNotEmpty() && want == normalizeShortCode(typed)
}

/** The promotion form of a Guest: the code the Admin types and the role to give. */
data class PromoteForm(
    val expectedCode: String?,
    val typedCode: String = "",
    val role: Role = Role.EDITOR,
) {
    val codeMatches: Boolean get() = shortCodesMatch(expectedCode, typedCode)

    /** Only when the codes match: the Admin promotes the person standing in front of them. */
    val canPromote: Boolean get() = codeMatches && role in PROMOTION_ROLES

    companion object {
        val PROMOTION_ROLES = listOf(Role.EDITOR, Role.VIEWER, Role.ADMIN)
    }
}

/** What happened last, shown once. */
enum class UserMessage { ROLE_CHANGED, DISABLED, ENABLED, DELETED, PROMOTION_PENDING, PROMOTION_CANCELLED, DEVICE_REVOKED, RENAMED }

data class AdminUsersState(
    val users: Loaded<List<AdminUser>> = Loaded(),
    /** An action runs: buttons are off. */
    val busy: Boolean = false,
    val failure: ActionFailure? = null,
    val message: UserMessage? = null,
    /** A reset QR code to show full screen, for [resetFor]. */
    val reset: QrGrant? = null,
    val resetFor: String? = null,
    val promote: PromoteForm? = null,
) {
    fun user(id: String): AdminUser? = users.data?.firstOrNull { it.id == id }
}

/** Users list and the actions of the user detail. */
class AdminUsersViewModel(private val api: AdminApi) : ViewModel() {
    private val _state = MutableStateFlow(AdminUsersState())
    val state: StateFlow<AdminUsersState> = _state.asStateFlow()

    private val users = MutableStateFlow(Loaded<List<AdminUser>>())
    private val loader = Loader(viewModelScope, users) { api.users().sortedWith(USER_ORDER) }

    init {
        viewModelScope.launch { users.collect { list -> _state.update { it.copy(users = list) } } }
    }

    /** Loads, or reloads with the current data shown (pull-to-refresh, screen opened again). */
    fun refresh() = loader.load(refresh = true)

    fun retry() = loader.load()

    fun changeRole(userId: String, role: Role) = act(UserMessage.ROLE_CHANGED) { api.changeRole(userId, role) }

    fun setDisabled(userId: String, disabled: Boolean) =
        act(if (disabled) UserMessage.DISABLED else UserMessage.ENABLED) { api.setDisabled(userId, disabled) }

    /** Only a valid name (1 to 50 characters, trimmed) is sent. */
    fun rename(userId: String, displayName: String) {
        val name = displayName.trim()
        if (!AccountRules.isValidDisplayName(name)) return
        act(UserMessage.RENAMED) { api.rename(userId, name) }
    }

    fun delete(userId: String) = act(UserMessage.DELETED) { api.deleteUser(userId) }

    fun revokeDevice(deviceId: String) = act(UserMessage.DEVICE_REVOKED) { api.revokeDevice(deviceId) }

    fun cancelPromotion(userId: String) = act(UserMessage.PROMOTION_CANCELLED) { api.cancelPromotion(userId) }

    fun resetPassword(userId: String) = act(null, reload = false) {
        val grant = api.resetPassword(userId)
        _state.update { it.copy(reset = grant, resetFor = userId) }
    }

    fun closeReset() = _state.update { it.copy(reset = null, resetFor = null) }

    fun startPromotion(userId: String) {
        val user = _state.value.user(userId) ?: return
        _state.update { it.copy(promote = PromoteForm(user.shortCode)) }
    }

    fun typePromoteCode(code: String) = _state.update { it.copy(promote = it.promote?.copy(typedCode = code)) }

    fun choosePromoteRole(role: Role) = _state.update { it.copy(promote = it.promote?.copy(role = role)) }

    fun closePromotion() = _state.update { it.copy(promote = null) }

    /** Sends the promotion only when the codes match. The Guest then chooses a username and password. */
    fun promote(userId: String) {
        val form = _state.value.promote ?: return
        if (!form.canPromote) return
        act(UserMessage.PROMOTION_PENDING) {
            // The app checked that the typed code matches: send the server's own spelling of it
            api.promote(userId, form.role, form.expectedCode ?: form.typedCode.trim())
            _state.update { it.copy(promote = null) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, failure = null) }

    private fun act(done: UserMessage?, reload: Boolean = true, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failure = null, message = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false, message = done) }
                if (reload) loader.load(refresh = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, failure = e.toFailure()) }
            }
        }
    }

    private companion object {
        /** Pending promotions first (they wait for the Admin), then full accounts, then Guests, by name. */
        val USER_ORDER = compareBy<AdminUser>({ it.pendingPromotion == null }, { it.isGuest }, { it.displayName.lowercase() })
    }
}

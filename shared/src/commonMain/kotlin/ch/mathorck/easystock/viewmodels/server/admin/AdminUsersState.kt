package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.AdminUser
import ch.mathorck.easystock.data.dto.server.QrGrant

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

package ch.mathorck.easystock.data.dto.server

import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.services.server.UserKind
import ch.mathorck.easystock.services.server.WireRole
import kotlinx.serialization.Serializable

/** A row of `GET /admin/users`. */
@Serializable
data class AdminUser(
    val id: String,
    val displayName: String = "",
    /** Null for a Guest (no username). */
    val username: String? = null,
    val role: String = "",
    /** [UserKind]. */
    val kind: String = UserKind.FULL,
    /** Blank for a full account, the code the Guest shows for a Guest. */
    val shortCode: String = "",
    val createdAt: String? = null,
    /** Last request of any of the user's devices: the "last sync" of the list. */
    val lastSeenAt: String? = null,
    /** Sent by the server as a plain boolean (no timestamp). */
    val disabled: Boolean = false,
    val devices: List<AdminDevice> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
) {
    val isGuest: Boolean get() = kind == UserKind.GUEST
    val appRole: Role? get() = WireRole.parse(role)
}

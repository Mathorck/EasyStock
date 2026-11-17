package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** A device session of `GET /admin/devices`, revoked ones included (server `AdminDeviceAnswer`). */
@Serializable
data class AdminDeviceSession(
    val id: String,
    val userId: String,
    val userDisplayName: String = "",
    val deviceName: String = "",
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null,
    val revokeReason: String? = null,
) {
    val revoked: Boolean get() = revokedAt != null
}

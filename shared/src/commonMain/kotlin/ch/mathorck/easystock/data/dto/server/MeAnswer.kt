package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `GET /me`: the user, role, kind, short code and the pending promotion if there is one. */
@Serializable
data class MeAnswer(
    val id: String = "",
    val displayName: String = "",
    val role: String = "",
    val kind: String = "",
    /** Blank for a full account. */
    val shortCode: String = "",
    val alertsEnabled: Boolean? = null,
    /** Same shape as in the admin user list ([PendingPromotion]). */
    val pendingPromotion: PendingPromotion? = null,
)

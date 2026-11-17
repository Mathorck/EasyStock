package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/**
 * The printed QR code (`/admin/public-invite`). [token] and [qr] may be missing when the server only keeps
 * a hash: then the PDF can only be made right after [regenerate][AdminApi.regeneratePublicInvite].
 */
@Serializable
data class PublicInvite(
    val enabled: Boolean = false,
    /** Most Guest accounts at a time, null = no limit. */
    val guestLimit: Int? = null,
    /** Guest accounts now. */
    val guestCount: Int = 0,
    val createdAt: String? = null,
    val token: String? = null,
    val qr: String? = null,
) {
    val grant: QrGrant get() = QrGrant(token, qr)

    override fun toString(): String = "PublicInvite(enabled=$enabled, guestLimit=$guestLimit, guestCount=$guestCount)"
}

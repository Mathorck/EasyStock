package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** An open invite. [token] and [qr] are only sent when it is created: the server keeps a hash. */
@Serializable
data class Invite(
    val id: String,
    val role: String = "",
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val token: String? = null,
    val qr: String? = null,
) {
    val grant: QrGrant get() = QrGrant(token, qr, expiresAt)

    override fun toString(): String = "Invite(id=$id, role=$role, expiresAt=$expiresAt)"
}

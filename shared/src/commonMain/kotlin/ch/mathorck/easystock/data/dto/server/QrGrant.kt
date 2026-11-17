package ch.mathorck.easystock.data.dto.server

import ch.mathorck.easystock.enums.server.JoinKind
import ch.mathorck.easystock.services.server.JoinCode
import ch.mathorck.easystock.services.server.ServerConnection
import kotlinx.serialization.Serializable

/**
 * A single-use token for a QR code (`POST /admin/users/{id}/reset`). The app builds the QR code itself
 * from [token] with the `sync_state` address ([JoinCode.build]), so it always has the app's format;
 * [qr] (the content built by the server) is used only when no token is sent.
 */
@Serializable
data class QrGrant(
    val token: String? = null,
    val qr: String? = null,
    val expiresAt: String? = null,
) {
    override fun toString(): String = "QrGrant(expiresAt=$expiresAt)"

    /**
     * The QR code text for [kind] on [connection]: built by the app from [token], else the server's [qr]
     * if it is a valid code of that kind. Null when there is neither.
     */
    fun qrText(connection: ServerConnection, kind: JoinKind): String? {
        token?.takeIf { it.isNotBlank() }?.let { token ->
            runCatching { JoinCode.build(connection.serverUrl, kind, token, connection.fingerprint) }
                .getOrNull()?.let { return it.toQrText() }
        }
        val fromServer = qr?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JoinCode.parse(fromServer) }.getOrNull()?.takeIf { it.kind == kind }?.toQrText()
    }
}

package ch.mathorck.easystock.data.dto.backup

import ch.mathorck.easystock.services.backup.KEY_BYTES
import ch.mathorck.easystock.services.backup.base64Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wi-Fi transfer ticket, shown as a QR code by the sender. Single use, valid a few minutes.
 * [token] proves the receiver scanned the QR code; [key] encrypts the stream (AES-256-GCM),
 * so the backup is never readable on the network even without a user password.
 */
@Serializable
data class WifiTicket(
    @SerialName("v") val version: Int = 1,
    @SerialName("h") val host: String,
    @SerialName("p") val port: Int,
    /** Base64url, no padding. */
    @SerialName("t") val token: String,
    /** Base64url, no padding. */
    @SerialName("k") val key: String,
) {
    fun tokenBytes(): ByteArray = base64Url.decode(token)

    fun keyBytes(): ByteArray = base64Url.decode(key)

    fun validate() {
        require(version == 1) { "Unknown ticket version" }
        require(port in 1..65535) { "Invalid port" }
        require(host.length <= 64 && host.isNotEmpty()) { "Invalid host" }
        require(tokenBytes().size == TOKEN_BYTES) { "Invalid token" }
        require(keyBytes().size == KEY_BYTES) { "Invalid key" }
    }

    // The key must not end up in logs
    override fun toString(): String = "WifiTicket(host=$host, port=$port)"

    companion object {
        const val TOKEN_BYTES = 16

        fun create(host: String, port: Int, token: ByteArray, key: ByteArray): WifiTicket =
            WifiTicket(host = host, port = port, token = base64Url.encode(token), key = base64Url.encode(key))
    }
}

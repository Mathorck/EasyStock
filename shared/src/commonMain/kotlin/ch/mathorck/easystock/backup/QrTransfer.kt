package ch.mathorck.easystock.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

// Two kinds of QR codes, both text in the QR alphanumeric alphabet (uppercase prefix + Base45):
//   ES1:<base45>   the backup itself: [QR_BUNDLE_MAGIC] + deflate(BackupBundle JSON), optionally encrypted
//   ESW1:<base45>  a Wi-Fi transfer ticket (JSON [WifiTicket]): where to download and the stream key

const val DATA_QR_PREFIX = "ES1:"
const val WIFI_QR_PREFIX = "ESW1:"

/** First bytes of a QR backup payload once decrypted. */
val QR_BUNDLE_MAGIC: ByteArray = "ESQ1".encodeToByteArray()

/**
 * Longest QR text offered. A version 40 QR code at medium error correction holds 3391 alphanumeric
 * characters; 2500 (about version 34) stays readable from another phone screen.
 * In bytes: [MAX_QR_PAYLOAD_BYTES].
 */
const val MAX_QR_TEXT_CHARS = 2500

/** Largest payload (after compression and encryption) that fits in [MAX_QR_TEXT_CHARS]. */
val MAX_QR_PAYLOAD_BYTES: Int = (MAX_QR_TEXT_CHARS - DATA_QR_PREFIX.length) / 3 * 2

/** Inflated size accepted from a QR payload. */
const val MAX_QR_INFLATED_BYTES = 4 * 1024 * 1024

/** Whether a backup can be sent as a single QR code. */
sealed interface QrDecision {
    data class Fits(val text: String) : QrDecision

    /** Too large, or has photos (never in a QR code): offer the Wi-Fi transfer or a file. */
    data object TooLarge : QrDecision
}

object QrTransfer {
    fun decide(payload: ByteArray, hasPhotos: Boolean): QrDecision =
        if (hasPhotos || payload.size > MAX_QR_PAYLOAD_BYTES) QrDecision.TooLarge else QrDecision.Fits(dataQrText(payload))

    /** Payload of a data QR: the bundle, compressed, encrypted when [secret] is set. */
    fun buildPayload(bundle: BackupBundle, secret: BackupSecret?): ByteArray {
        val plain = QR_BUNDLE_MAGIC + BackupCompression.deflate(BackupCodec.encodeBundle(bundle))
        return if (secret == null) plain else BackupCipher.encrypt(plain, secret)
    }

    /** Opposite of [buildPayload] once decrypted. */
    fun readBundle(plain: ByteArray): BackupBundle {
        if (!startsWith(plain, QR_BUNDLE_MAGIC)) throw BackupException(BackupError.NOT_A_BACKUP, "No bundle magic")
        val json = BackupCompression.inflate(plain.copyOfRange(QR_BUNDLE_MAGIC.size, plain.size), MAX_QR_INFLATED_BYTES)
        return BackupCodec.decodeBundle(json)
    }

    fun dataQrText(payload: ByteArray): String = DATA_QR_PREFIX + Base45.encode(payload)

    fun wifiQrText(ticket: WifiTicket): String =
        WIFI_QR_PREFIX + Base45.encode(backupJson.encodeToString(WifiTicket.serializer(), ticket).encodeToByteArray())

    /** Reads a scanned text. Throws [BackupException] ([BackupError.NOT_A_BACKUP]) for any other QR code. */
    fun parse(text: String): ScannedQr = try {
        when {
            text.startsWith(DATA_QR_PREFIX) -> ScannedQr.Data(Base45.decode(text.substring(DATA_QR_PREFIX.length)))
            text.startsWith(WIFI_QR_PREFIX) -> {
                val json = Base45.decode(text.substring(WIFI_QR_PREFIX.length)).decodeToString()
                ScannedQr.Wifi(backupJson.decodeFromString(WifiTicket.serializer(), json).also { it.validate() })
            }
            else -> throw BackupException(BackupError.NOT_A_BACKUP, "Unknown QR code")
        }
    } catch (e: BackupException) {
        throw e
    } catch (e: Exception) {
        throw BackupException(BackupError.NOT_A_BACKUP, "Invalid QR code", e)
    }
}

sealed interface ScannedQr {
    /** A backup payload (maybe encrypted). */
    class Data(val payload: ByteArray) : ScannedQr

    /** Where to download a backup on the local network. */
    data class Wifi(val ticket: WifiTicket) : ScannedQr
}

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

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean =
    bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

/** True for a local network address (private IPv4 ranges, link-local, loopback, IPv6 ULA). */
fun isLocalNetworkAddress(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size == 4) {
        val octets = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 && it == n.toString() } ?: return false }
        val (a, b) = octets
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || a == 127
    }
    val lower = host.lowercase()
    if (':' !in lower || lower.any { it !in "0123456789abcdef:%" && !it.isLetterOrDigit() }) return false
    return lower.startsWith("fe8") || lower.startsWith("fe9") || lower.startsWith("fea") || lower.startsWith("feb") ||
        lower.startsWith("fc") || lower.startsWith("fd") || lower == "::1"
}

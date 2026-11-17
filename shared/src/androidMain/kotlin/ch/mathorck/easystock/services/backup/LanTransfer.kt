package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.WifiTicket
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.exceptions.backup.BackupException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

// Local network transfer, plain TCP (pure JVM: runs on the device and in the host tests).
//   receiver -> sender: "ESW1 <token base64url>\n"
//   sender -> receiver: "OK <size>\n", then the file in the encrypted envelope (raw ticket key)
// A wrong token gets the connection closed. The sender serves a single download, then stops.
// A peer address that sent [MAX_FAILED_ATTEMPTS] wrong tokens is ignored from then on; other peers
// can still connect: a stranger on the same Wi-Fi can't block the transfer (the 128-bit token can't
// be guessed, and the QR code expires after [WIFI_TRANSFER_TIMEOUT]).

internal const val HELLO = "ESW1"
private const val MAX_LINE = 128
internal const val MAX_FAILED_ATTEMPTS = 3

/** How long the QR code of a Wi-Fi transfer stays valid. */
val WIFI_TRANSFER_TIMEOUT: Duration = 5.minutes
internal val CONNECT_TIMEOUT = 10.seconds
internal val READ_TIMEOUT = 30.seconds

internal val tokenCodec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/**
 * Downloads the file of [ticket] to [target] (decrypted, as the sender had it). [target] is only
 * complete when this returns. Throws [BackupException]: [BackupError.UNREACHABLE], [BackupError.TIMEOUT],
 * [BackupError.DAMAGED], [BackupError.TOO_LARGE].
 */
fun lanDownload(ticket: WifiTicket, target: File, maxBytes: Long, onProgress: (Long) -> Unit) {
    if (!isLocalNetworkAddress(ticket.host)) throw BackupException(BackupError.UNREACHABLE, "Not a local address")
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByName(ticket.host), ticket.port), CONNECT_TIMEOUT.inWholeMilliseconds.toInt())
            socket.soTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
            socket.getOutputStream().apply {
                write("$HELLO ${ticket.token}\n".encodeToByteArray())
                flush()
            }
            val input = socket.getInputStream().buffered(64 * 1024)
            val answer = readLine(input) ?: throw BackupException(BackupError.UNREACHABLE, "Refused")
            val size = answer.removePrefix("OK ").toLongOrNull()
            if (!answer.startsWith("OK ") || size == null || size < 0) throw BackupException(BackupError.UNREACHABLE, "Refused")
            if (size > maxBytes) throw BackupException(BackupError.TOO_LARGE, "Too large")

            val source = input.asByteSource()
            val header = BackupCipher.readHeader(source)
            val key = BackupCipher.unlock(header, BackupSecret.Key(ticket.keyBytes()))
            var received = 0L
            target.outputStream().buffered(64 * 1024).use { out ->
                BackupCipher.decrypt(header, key, source) { buffer, offset, length ->
                    received += length
                    if (received > maxBytes) throw BackupException(BackupError.TOO_LARGE, "Too large")
                    out.write(buffer, offset, length)
                    onProgress(received)
                }
            }
            key.fill(0)
        }
    } catch (e: BackupException) {
        target.delete()
        // The raw key comes from the QR code: a key check failure is a damaged stream, not a password
        throw if (e.error == BackupError.WRONG_PASSWORD || e.error == BackupError.PASSWORD_REQUIRED) {
            BackupException(BackupError.DAMAGED, "Key check", e)
        } else {
            e
        }
    } catch (e: SocketTimeoutException) {
        target.delete()
        throw BackupException(BackupError.TIMEOUT, "Timeout", e)
    } catch (e: ConnectException) {
        target.delete()
        throw BackupException(BackupError.UNREACHABLE, "Connect", e)
    } catch (e: NoRouteToHostException) {
        target.delete()
        throw BackupException(BackupError.UNREACHABLE, "No route", e)
    } catch (e: IOException) {
        target.delete()
        throw BackupException(BackupError.UNREACHABLE, "Connection lost", e)
    }
}

/** An ASCII line without "\n", null at the end of the stream. Longer than [MAX_LINE]: null. */
internal fun readLine(input: InputStream): String? {
    val bytes = ByteArray(MAX_LINE)
    var size = 0
    while (true) {
        val b = input.read()
        if (b < 0) return null
        if (b == '\n'.code) return bytes.decodeToString(0, size)
        if (size == MAX_LINE) return null
        bytes[size++] = b.toByte()
    }
}

package ch.mathorck.easystock.backup

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

// Local network transfer, plain TCP (pure JVM: runs on the device and in the host tests).
//   receiver -> sender: "ESW1 <token base64url>\n"
//   sender -> receiver: "OK <size>\n", then the file in the encrypted envelope (raw ticket key)
// A wrong token gets the connection closed. The sender serves a single download, then stops.

private const val HELLO = "ESW1"
private const val MAX_LINE = 128
private const val MAX_FAILED_ATTEMPTS = 3

/** How long the QR code of a Wi-Fi transfer stays valid. */
val WIFI_TRANSFER_TIMEOUT: Duration = 5.minutes
private val CONNECT_TIMEOUT = 10.seconds
private val READ_TIMEOUT = 30.seconds

private val tokenCodec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/**
 * Serves [file] once on [address] (random port). The stream is encrypted with a new random key,
 * shared with the receiver only through the QR code ([ticket]).
 */
class LanSender(
    private val file: File,
    address: InetAddress,
    private val timeout: Duration = WIFI_TRANSFER_TIMEOUT,
) : WifiSender {
    private val server = ServerSocket(0, 1, address)
    private val token = BackupCrypto.randomBytes(WifiTicket.TOKEN_BYTES)
    private val key = BackupCrypto.randomBytes(KEY_BYTES)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<WifiState>(WifiState.Waiting)

    override val ticket: WifiTicket = WifiTicket.create(address.hostAddress!!.substringBefore('%'), server.localPort, token, key)
    override val state: StateFlow<WifiState> = _state

    private val job: Job = scope.launch { serve() }

    private suspend fun serve() {
        val start = TimeSource.Monotonic.markNow()
        var failures = 0
        try {
            while (scope.isActive) {
                val left = timeout - start.elapsedNow()
                if (left <= Duration.ZERO) {
                    _state.value = WifiState.Expired
                    return
                }
                server.soTimeout = left.inWholeMilliseconds.coerceIn(1, 1000).toInt()
                val socket = try {
                    runInterruptible { server.accept() }
                } catch (e: SocketTimeoutException) {
                    continue
                }
                socket.use {
                    if (handle(it)) {
                        _state.value = WifiState.Done
                        return
                    }
                }
                if (++failures >= MAX_FAILED_ATTEMPTS) {
                    _state.value = WifiState.Blocked
                    return
                }
            }
        } catch (e: SocketTimeoutException) {
            _state.value = WifiState.Failed(BackupError.TIMEOUT)
        } catch (e: SocketException) {
            // Closed by stop(), or the receiver left
            if (_state.value is WifiState.Waiting || _state.value is WifiState.Sending) _state.value = WifiState.Failed(BackupError.UNREACHABLE)
        } catch (e: BackupException) {
            _state.value = WifiState.Failed(e.error)
        } catch (e: IOException) {
            _state.value = WifiState.Failed(BackupError.FAILED)
        } finally {
            server.close()
            key.fill(0)
        }
    }

    /** True once the file was sent, false for a wrong token. Throws on a failed transfer. */
    private fun handle(socket: Socket): Boolean {
        socket.soTimeout = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
        val line = try {
            readLine(socket.getInputStream())
        } catch (e: IOException) {
            return false
        } ?: return false
        val parts = line.split(' ')
        val received = runCatching { tokenCodec.decode(parts.getOrElse(1) { "" }) }.getOrNull()
        if (parts.size != 2 || parts[0] != HELLO || received == null || !MessageDigest.isEqual(received, token)) return false

        socket.soTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
        val total = file.length()
        _state.value = WifiState.Sending(0, total)
        val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
        out.write("OK $total\n".encodeToByteArray())
        var sent = 0L
        FileInputStream(file).use { input ->
            val sink = BackupCipher.encryptingSink(out.asByteSink(), BackupSecret.Key(key))
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                sink.write(buffer, 0, n)
                sent += n
                _state.value = WifiState.Sending(sent, total)
            }
            sink.finish()
        }
        out.flush()
        socket.shutdownOutput()
        // Waits for the receiver to close: it has read everything
        runCatching { socket.getInputStream().read() }
        return true
    }

    override fun stop() {
        if (_state.value is WifiState.Waiting || _state.value is WifiState.Sending) _state.value = WifiState.Expired
        runCatching { server.close() }
        job.cancel()
        scope.cancel()
    }
}

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
private fun readLine(input: InputStream): String? {
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

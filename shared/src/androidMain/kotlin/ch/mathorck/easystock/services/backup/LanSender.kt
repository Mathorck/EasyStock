package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.WifiTicket
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.exceptions.backup.BackupException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.time.Duration
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

/**
 * Serves [file] once on [address] (random port). The stream is encrypted with a new random key,
 * shared with the receiver only through the QR code ([ticket]).
 */
class LanSender(
    private val file: File,
    address: InetAddress,
    override val timeout: Duration = WIFI_TRANSFER_TIMEOUT,
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
        val failures = HashMap<InetAddress, Int>()
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
                val peer = socket.inetAddress
                // A blocked peer is closed at once, without reading anything
                if (failures.getOrElse(peer) { 0 } >= MAX_FAILED_ATTEMPTS) {
                    socket.close()
                    continue
                }
                socket.use {
                    if (handle(it)) {
                        _state.value = WifiState.Done
                        return
                    }
                }
                failures[peer] = failures.getOrElse(peer) { 0 } + 1
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

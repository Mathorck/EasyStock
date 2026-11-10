package ch.mathorck.easystock.backup

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Wi-Fi transfer over the loopback interface. */
class LanTransferTest {
    private val dir: File = Files.createTempDirectory("lan-test").toFile()
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val content = Random(3).nextBytes(300_000)
    private val source = File(dir, "source").apply { writeBytes(content) }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun awaitState(sender: LanSender, predicate: (WifiState) -> Boolean): WifiState =
        runBlocking { withTimeout(10.seconds) { sender.state.first(predicate) } }

    @Test
    fun fileIsDownloadedOnce() {
        val sender = LanSender(source, loopback)
        try {
            val target = File(dir, "target")
            var progress = 0L
            lanDownload(sender.ticket, target, Long.MAX_VALUE) { progress = it }

            assertContentEquals(content, target.readBytes())
            assertEquals(content.size.toLong(), progress)
            assertEquals(WifiState.Done, awaitState(sender) { it == WifiState.Done })
            // Single download: the port is closed
            val error = assertFailsWith<BackupException> { lanDownload(sender.ticket, File(dir, "again"), Long.MAX_VALUE) {} }
            assertEquals(BackupError.UNREACHABLE, error.error)
        } finally {
            sender.stop()
        }
    }

    @Test
    fun wrongTokenIsRefusedAndBlocksAfterThreeAttempts() {
        val sender = LanSender(source, loopback)
        try {
            val forged = sender.ticket.copy(token = WifiTicket.create("x", 1, ByteArray(16), ByteArray(32)).token)
            repeat(3) {
                val error = assertFailsWith<BackupException> { lanDownload(forged, File(dir, "t$it"), Long.MAX_VALUE) {} }
                assertEquals(BackupError.UNREACHABLE, error.error)
                assertFalse(File(dir, "t$it").exists())
            }
            assertEquals(WifiState.Blocked, awaitState(sender) { it == WifiState.Blocked })
            // Even the right token is refused now
            assertFailsWith<BackupException> { lanDownload(sender.ticket, File(dir, "right"), Long.MAX_VALUE) {} }
        } finally {
            sender.stop()
        }
    }

    @Test
    fun wrongKeyIsDamaged() {
        val sender = LanSender(source, loopback)
        try {
            val wrongKey = sender.ticket.copy(key = WifiTicket.create("x", 1, ByteArray(16), ByteArray(32)).key)
            val error = assertFailsWith<BackupException> { lanDownload(wrongKey, File(dir, "target"), Long.MAX_VALUE) {} }
            assertEquals(BackupError.DAMAGED, error.error)
            assertFalse(File(dir, "target").exists())
        } finally {
            sender.stop()
        }
    }

    @Test
    fun streamIsEncrypted() {
        val sender = LanSender(source, loopback)
        try {
            val raw = Socket(loopback, sender.ticket.port).use { socket ->
                socket.getOutputStream().write("ESW1 ${sender.ticket.token}\n".encodeToByteArray())
                socket.getInputStream().readBytes()
            }
            // "OK <size>\n" then the envelope: the file content never appears in clear
            val body = raw.copyOfRange(raw.indexOf('\n'.code.toByte()) + 1, raw.size)
            assertEquals(BackupKind.ENCRYPTED, backupKind(body))
            val sample = content.copyOfRange(1000, 1016)
            assertFalse((0..body.size - sample.size).any { start -> sample.indices.all { body[start + it] == sample[it] } })
        } finally {
            sender.stop()
        }
    }

    @Test
    fun expiredTransferIsUnreachable() {
        val sender = LanSender(source, loopback, timeout = 300.milliseconds)
        try {
            assertEquals(WifiState.Expired, awaitState(sender) { it == WifiState.Expired })
            val error = assertFailsWith<BackupException> { lanDownload(sender.ticket, File(dir, "target"), Long.MAX_VALUE) {} }
            assertEquals(BackupError.UNREACHABLE, error.error)
        } finally {
            sender.stop()
        }
    }

    @Test
    fun publicAddressIsRefused() {
        val ticket = WifiTicket.create("8.8.8.8", 4000, ByteArray(16), ByteArray(32))
        val error = assertFailsWith<BackupException> { lanDownload(ticket, File(dir, "target"), Long.MAX_VALUE) {} }
        assertEquals(BackupError.UNREACHABLE, error.error)
    }

    @Test
    fun oversizedDownloadIsRefused() {
        val sender = LanSender(source, loopback)
        try {
            val error = assertFailsWith<BackupException> { lanDownload(sender.ticket, File(dir, "target"), 1000) {} }
            assertEquals(BackupError.TOO_LARGE, error.error)
        } finally {
            sender.stop()
        }
    }

    private fun ByteArray.indexOf(b: Byte): Int = indexOfFirst { it == b }
}

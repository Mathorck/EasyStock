package ch.mathorck.easystock.server

import ch.mathorck.easystock.backup.QrTransfer
import ch.mathorck.easystock.backup.ScannedQr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JoinCodeBuildTest {
    private val fp = "ab".repeat(32)

    @Test
    fun buildsTheDocumentedFormat() {
        val code = JoinCode.build("http://192.168.1.20:8080", JoinKind.INVITE, "abc123")
        assertEquals("easystock://join?u=http%3A%2F%2F192.168.1.20%3A8080&k=invite&t=abc123", code.toQrText())
    }

    @Test
    fun addsTheFingerprintLast() {
        val code = JoinCode.build("https://nas.local", JoinKind.RESET, "t", fp)
        assertEquals("easystock://join?u=https%3A%2F%2Fnas.local&k=reset&t=t&fp=$fp", code.toQrText())
    }

    @Test
    fun parseGivesBackEveryKind() {
        JoinKind.entries.forEach { kind ->
            val code = JoinCode.build("https://stock.example.org/easystock", kind, "q3V0-_x~", fp)
            assertEquals(code, JoinCode.parse(code.toQrText()))
            assertEquals(code, assertIs<ScannedQr.Join>(QrTransfer.parse(code.toQrText())).code)
        }
    }

    @Test
    fun encodesReservedCharactersOfTheToken() {
        val token = "a+b/c=d&e%f?g#h"
        val code = JoinCode.build("http://nas:8080", JoinKind.GUEST, token)
        val text = code.toQrText()
        assertTrue("t=a%2Bb%2Fc%3Dd%26e%25f%3Fg%23h" in text, text)
        assertEquals(token, JoinCode.parse(text).token)
    }

    @Test
    fun encodesAnIpv6Address() {
        val code = JoinCode.build("http://[fd00::12]:8080", JoinKind.INVITE, "t")
        assertEquals("http://[fd00::12]:8080", JoinCode.parse(code.toQrText()).serverUrl)
    }

    @Test
    fun dropsATrailingSlashAndNormalizesTheFingerprint() {
        val code = JoinCode.build("https://nas.local/", JoinKind.INVITE, "t", fp.uppercase())
        assertEquals("https://nas.local", code.serverUrl)
        assertEquals(fp, code.fingerprint)
    }

    @Test
    fun refusesWhatTheScannerWouldRefuse() {
        assertFailsWith<IllegalArgumentException> { JoinCode.build("ftp://nas", JoinKind.INVITE, "t") }
        assertFailsWith<IllegalArgumentException> { JoinCode.build("http://nas", JoinKind.INVITE, "") }
        assertFailsWith<IllegalArgumentException> { JoinCode.build("http://nas", JoinKind.INVITE, "t", fp) }
        assertFailsWith<IllegalArgumentException> { JoinCode.build("https://nas", JoinKind.INVITE, "t", "12:34") }
        assertFailsWith<IllegalArgumentException> { JoinCode.build("http://nas", JoinKind.INVITE, "with space") }
    }

    @Test
    fun theTokenStaysOutOfToString() {
        val code = JoinCode.build("http://nas", JoinKind.RESET, "secret-token")
        assertTrue("secret-token" !in code.toString())
    }
}

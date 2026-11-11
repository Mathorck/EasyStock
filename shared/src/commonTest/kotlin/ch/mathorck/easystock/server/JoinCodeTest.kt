package ch.mathorck.easystock.server

import ch.mathorck.easystock.backup.BackupError
import ch.mathorck.easystock.backup.BackupException
import ch.mathorck.easystock.backup.QrTransfer
import ch.mathorck.easystock.backup.ScannedQr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinCodeTest {
    private val fp = "ab".repeat(32)

    private fun join(query: String) = "easystock://join?$query"

    @Test
    fun readsEveryKind() {
        JoinKind.entries.forEach { kind ->
            val code = JoinCode.parse(join("u=https%3A%2F%2Fstock.example.org&k=${kind.code}&t=tok123"))
            assertEquals(JoinCode("https://stock.example.org", kind, "tok123"), code)
            assertTrue(code.isHttps)
        }
    }

    @Test
    fun theScannerReturnsAJoinCase() {
        val scanned = QrTransfer.parse(join("u=http://192.168.1.20:8080&k=guest&t=abc"))
        val code = assertIs<ScannedQr.Join>(scanned).code
        assertEquals("http://192.168.1.20:8080", code.serverUrl)
        assertEquals(JoinKind.GUEST, code.kind)
        assertNull(code.fingerprint)
        assertFalse(code.isHttps)
    }

    @Test
    fun theFingerprintIsOptionalAndNormalized() {
        val plain = JoinCode.parse(join("u=https://10.0.0.5&k=invite&t=x&fp=$fp"))
        assertEquals(fp, plain.fingerprint)
        val upper = JoinCode.parse(join("u=https://10.0.0.5&k=invite&t=x&fp=${fp.uppercase()}"))
        assertEquals(fp, upper.fingerprint)
        val colons = fp.chunked(2).joinToString(":").uppercase()
        assertEquals(fp, JoinCode.parse(join("u=https://10.0.0.5&k=invite&t=x&fp=${colons.replace(":", "%3A")}")).fingerprint)
        assertEquals(fp, JoinCode.parse(join("u=https://10.0.0.5&k=invite&t=x&fp=$colons")).fingerprint)
    }

    @Test
    fun parametersInAnyOrderAndUnknownOnesAreIgnored() {
        val code = JoinCode.parse(join("v=2&t=tok&k=setup&extra=1&u=https%3A%2F%2Fhost.lan%3A8443%2Feasystock%2F"))
        assertEquals(JoinCode("https://host.lan:8443/easystock", JoinKind.SETUP, "tok"), code)
    }

    @Test
    fun acceptsIpv6Hosts() {
        assertEquals("http://[fd00::1]:8080", JoinCode.parse(join("u=http://[fd00::1]:8080&k=guest&t=a")).serverUrl)
    }

    @Test
    fun theTokenIsNotInToString() {
        val code = JoinCode.parse(join("u=https://h&k=reset&t=SECRET-TOKEN"))
        assertFalse("SECRET-TOKEN" in code.toString())
        assertFalse("SECRET-TOKEN" in ScannedQr.Join(code).toString())
    }

    @Test
    fun malformedCodesAreRejected() {
        val bad = listOf(
            // Missing or empty parameters
            join(""),
            join("k=guest&t=a"),
            join("u=&k=guest&t=a"),
            join("u=https://h&t=a"),
            join("u=https://h&k=&t=a"),
            join("u=https://h&k=guest"),
            join("u=https://h&k=guest&t="),
            // Unknown kind (case-sensitive)
            join("u=https://h&k=admin&t=a"),
            join("u=https://h&k=GUEST&t=a"),
            // Repeated parameter
            join("u=https://h&k=guest&t=a&t=b"),
            join("u=https://h&u=https://other&k=guest&t=a"),
            // Bad parameter syntax
            join("u=https://h&k=guest&t=a&"),
            join("u=https://h&&k=guest&t=a"),
            join("u=https://h&k=guest&t=a&=x"),
            join("u=https://h&k=guest&t=a&noequals"),
            // Bad percent-encoding
            join("u=https%3A%2F%2Fh%2&k=guest&t=a"),
            join("u=https%ZZ&k=guest&t=a"),
            join("u=https://h&k=guest&t=%C3%28"),
            // Invalid URL
            join("u=ftp://h&k=guest&t=a"),
            join("u=h.example&k=guest&t=a"),
            join("u=https://&k=guest&t=a"),
            join("u=https://user%40h&k=guest&t=a"),
            join("u=https://h:0&k=guest&t=a"),
            join("u=https://h:70000&k=guest&t=a"),
            join("u=https://h:80x&k=guest&t=a"),
            join("u=https://h%20x&k=guest&t=a"),
            join("u=https://h%3Fq%3D1&k=guest&t=a"),
            join("u=https://[fd00::1&k=guest&t=a"),
            join("u=https://h&k=guest&t=a#frag"),
            // Token with a space or a control character
            join("u=https://h&k=guest&t=a%20b"),
            join("u=https://h&k=guest&t=a%0A"),
            join("u=https://h&k=guest&t=" + "x".repeat(JoinCode.MAX_TOKEN_LENGTH + 1)),
            // Invalid fingerprint
            join("u=https://h&k=guest&t=a&fp="),
            join("u=https://h&k=guest&t=a&fp=abc"),
            join("u=https://h&k=guest&t=a&fp=${"g".repeat(64)}"),
            join("u=https://h&k=guest&t=a&fp=${"a".repeat(66)}"),
            join("u=https://h&k=guest&t=a&fp=${fp.chunked(4).joinToString(":")}"),
            // A fingerprint is only for HTTPS
            join("u=http://h&k=guest&t=a&fp=$fp"),
            // Too long
            join("u=https://h&k=guest&t=a&x=" + "y".repeat(JoinCode.MAX_TEXT_LENGTH)),
        )
        bad.forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { JoinCode.parse(text) }
            val e = assertFailsWith<BackupException>(text) { QrTransfer.parse(text) }
            assertEquals(BackupError.NOT_A_BACKUP, e.error, text)
        }
    }

    @Test
    fun readsTheServerPlanFormat() {
        JoinKind.entries.forEach { kind ->
            val text = "easystock://${kind.code}?server=https%3A%2F%2Fstock.example.org%2F&token=tok-123_x&fp=${fp.uppercase()}"
            assertEquals(JoinCode("https://stock.example.org", kind, "tok-123_x", fp), JoinCode.parse(text))
            assertEquals(kind, assertIs<ScannedQr.Join>(QrTransfer.parse(text)).code.kind)
        }
        // Parameters in any order, no fingerprint
        assertEquals(
            JoinCode("http://192.168.1.20:8080", JoinKind.INVITE, "abc"),
            JoinCode.parse("easystock://invite?token=abc&server=http://192.168.1.20:8080"),
        )
    }

    @Test
    fun theServerPlanFormatIsCheckedLikeTheAppOne() {
        listOf(
            "easystock://invite?token=abc",
            "easystock://invite?server=https://h",
            "easystock://invite?server=ftp://h&token=abc",
            "easystock://invite?server=http://h&token=abc&fp=$fp",
            "easystock://invite?u=https://h&t=abc",
            "easystock://admin?server=https://h&token=abc",
            "easystock://invite?server=https://h&token=abc&token=def",
        ).forEach {
            assertFailsWith<IllegalArgumentException>(it) { JoinCode.parse(it) }
        }
        assertFailsWith<BackupException> { QrTransfer.parse("easystock://admin?server=https://h&token=abc") }
    }

    @Test
    fun theAppStillBuildsTheJoinFormat() {
        val code = JoinCode.parse("easystock://guest?server=https://h&token=abc")
        assertTrue(code.toQrText().startsWith(JOIN_QR_PREFIX))
        assertEquals(code, JoinCode.parse(code.toQrText()))
    }

    @Test
    fun otherPrefixesAreNotJoinCodes() {
        listOf("EASYSTOCK://join?u=https://h&k=guest&t=a", "easystock://joinu=https://h", "https://h/join?k=guest").forEach {
            assertFailsWith<IllegalArgumentException> { JoinCode.parse(it) }
            assertFailsWith<BackupException> { QrTransfer.parse(it) }
        }
    }

    @Test
    fun normalizeFingerprintFormats() {
        assertEquals(fp, normalizeFingerprint(fp))
        assertEquals(fp, normalizeFingerprint(fp.uppercase()))
        assertEquals(fp, normalizeFingerprint(fp.chunked(2).joinToString(":")))
        assertNull(normalizeFingerprint(""))
        assertNull(normalizeFingerprint(fp.dropLast(2)))
        assertNull(normalizeFingerprint(":" + fp))
    }
}

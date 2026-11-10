package ch.mathorck.easystock.backup

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCipherTest {
    // Low iteration count keeps the tests fast; the format stores it in the header
    private fun password(text: String = "correct horse") = BackupSecret.Password(text.toCharArray(), iterations = 1_000)
    private val chunk = 1024

    private fun data(size: Int) = Random(42).nextBytes(size)

    @Test
    fun roundTripWithPassword() {
        val plain = data(5000)
        val encrypted = BackupCipher.encrypt(plain, password(), chunk)

        assertTrue(BackupCipher.isEncrypted(encrypted))
        assertContentEquals(plain, BackupCipher.decrypt(encrypted, password()))
    }

    @Test
    fun roundTripWithRawKey() {
        val key = BackupSecret.Key(BackupCrypto.randomBytes(KEY_BYTES))
        val plain = data(3000)

        assertContentEquals(plain, BackupCipher.decrypt(BackupCipher.encrypt(plain, key, chunk), key))
    }

    @Test
    fun roundTripAtChunkBoundaries() {
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 2 * chunk, 3 * chunk + 7)) {
            val plain = data(size)
            assertContentEquals(plain, BackupCipher.decrypt(BackupCipher.encrypt(plain, password(), chunk), password()), "size $size")
        }
    }

    @Test
    fun ciphertextDiffersEachTime() {
        val plain = data(100)
        assertFalse(BackupCipher.encrypt(plain, password(), chunk).contentEquals(BackupCipher.encrypt(plain, password(), chunk)))
    }

    @Test
    fun plainDataIsNotEncrypted() {
        assertFalse(BackupCipher.isEncrypted("PK\u0003\u0004".encodeToByteArray()))
        assertFalse(BackupCipher.isEncrypted(ByteArray(2)))
    }

    @Test
    fun wrongPasswordIsReported() {
        val encrypted = BackupCipher.encrypt(data(100), password("right"), chunk)

        val error = assertFailsWith<BackupException> { BackupCipher.decrypt(encrypted, password("wrong")) }
        assertEquals(BackupError.WRONG_PASSWORD, error.error)
    }

    @Test
    fun passwordFileNeedsAPassword() {
        val encrypted = BackupCipher.encrypt(data(100), password(), chunk)
        val header = BackupCipher.readHeader(encrypted.asByteSource())

        assertTrue(header.needsPassword)
        val error = assertFailsWith<BackupException> { BackupCipher.decrypt(encrypted, BackupSecret.Key(ByteArray(KEY_BYTES))) }
        assertEquals(BackupError.PASSWORD_REQUIRED, error.error)
    }

    @Test
    fun tamperedChunkIsDamaged() {
        val encrypted = BackupCipher.encrypt(data(3000), password(), chunk)
        // Last byte: tag of the last chunk
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { BackupCipher.decrypt(tampered, password()) }.error)
    }

    @Test
    fun tamperedCiphertextIsDamaged() {
        val encrypted = BackupCipher.encrypt(data(3000), password(), chunk)
        val tampered = encrypted.copyOf().also { it[200] = (it[200].toInt() xor 0x40).toByte() }

        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { BackupCipher.decrypt(tampered, password()) }.error)
    }

    @Test
    fun truncatedAtChunkBoundaryIsDamaged() {
        val encrypted = BackupCipher.encrypt(data(3 * chunk), password(), chunk)
        val headerSize = BackupCipher.readHeader(encrypted.asByteSource()).bytes.size
        // Drop the last chunk (an empty final chunk is 16 bytes): the previous one wasn't marked last
        val truncated = encrypted.copyOf(headerSize + 2 * (chunk + 16))

        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { BackupCipher.decrypt(truncated, password()) }.error)
    }

    @Test
    fun appendedDataIsDamaged() {
        val encrypted = BackupCipher.encrypt(data(100), password(), chunk) + ByteArray(20)

        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { BackupCipher.decrypt(encrypted, password()) }.error)
    }

    @Test
    fun tamperedHeaderIsRejected() {
        val encrypted = BackupCipher.encrypt(data(100), password(), chunk)
        // Byte in the salt: the key check fails
        val tampered = encrypted.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }

        assertFailsWith<BackupException> { BackupCipher.decrypt(tampered, password()) }
    }

    @Test
    fun forgedIterationCountIsRejected() {
        val encrypted = BackupCipher.encrypt(data(100), password(), chunk)
        // Iterations at offset 6: 0x7FFFFFFF would block the phone for hours
        val forged = encrypted.copyOf().also { it[6] = 0x7F; it[7] = -1; it[8] = -1; it[9] = -1 }

        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { BackupCipher.readHeader(forged.asByteSource()) }.error)
    }

    @Test
    fun newerEnvelopeVersionIsRejected() {
        val encrypted = BackupCipher.encrypt(data(10), password(), chunk).also { it[4] = 9 }

        assertEquals(BackupError.NEWER_VERSION, assertFailsWith<BackupException> { BackupCipher.readHeader(encrypted.asByteSource()) }.error)
    }

    @Test
    fun pbkdf2MatchesRfc7914Vector() {
        // RFC 7914 section 11, PBKDF2-HMAC-SHA256("passwd", "salt", 1, 64), first 32 bytes
        val key = BackupCrypto.pbkdf2Sha256("passwd".toCharArray(), "salt".encodeToByteArray(), 1, 32)
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", key.toHex())
    }

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

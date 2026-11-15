package ch.mathorck.easystock.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedSessionStoreTest {
    private val dir: File = Files.createTempDirectory("session").toFile()
    private var scope: CoroutineScope? = null

    @AfterTest
    fun cleanUp() = runBlocking {
        closeStore()
        dir.deleteRecursively()
        Unit
    }

    /**
     * Closes the current store. DataStore releases its file only when its scope's job has
     * completed (not just been cancelled), so wait for it before opening the file again.
     */
    private suspend fun closeStore() {
        scope?.coroutineContext?.job?.cancelAndJoin()
        scope = null
    }

    /** A new store on the same file, as after an app restart. */
    private suspend fun open(cipher: TokenCipher = FakeCipher(1)): EncryptedSessionStore {
        closeStore()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scope = it }
        return EncryptedSessionStore(PreferenceDataStoreFactory.create(scope = newScope) { File(dir, "session.preferences_pb") }, cipher)
    }

    private fun fileBytes(): String = File(dir, "session.preferences_pb").readBytes().decodeToString()

    @Test
    fun theRefreshTokenSurvivesARestartAndIsEncrypted() = runBlocking {
        open().saveRefreshToken("refresh-SECRET")
        assertFalse("refresh-SECRET" in fileBytes())
        assertEquals("refresh-SECRET", open().refreshToken())
    }

    @Test
    fun theAccessTokenStaysInMemory() = runBlocking {
        val store = open()
        store.accessToken = "access-SECRET"
        store.saveRefreshToken("r")
        assertFalse("access-SECRET" in fileBytes())
        assertNull(open().accessToken)
    }

    @Test
    fun nothingStoredGivesNull() = runBlocking {
        assertNull(open().refreshToken())
    }

    @Test
    fun clearForgetsBothTokens() = runBlocking {
        val store = open()
        store.accessToken = "a"
        store.saveRefreshToken("r")
        store.clear()
        assertNull(store.accessToken)
        assertNull(store.refreshToken())
        assertNull(open().refreshToken())
    }

    @Test
    fun aTokenThatCantBeDecryptedIsDeleted() = runBlocking {
        open(FakeCipher(1)).saveRefreshToken("r")
        // Other key: Keystore key lost, or the file restored on another phone
        assertNull(open(FakeCipher(2)).refreshToken())
        // Deleted, not just unreadable
        assertNull(open(FakeCipher(1)).refreshToken())
    }

    @Test
    fun aMissingOrUnreadableTokenIsALocalLoss() = runBlocking {
        val store = open(FakeCipher(1))
        assertNull(store.refreshToken())
        assertTrue(store.refreshTokenMissing)
        store.saveRefreshToken("r")
        assertEquals("r", store.refreshToken())
        assertFalse(store.refreshTokenMissing)

        val other = open(FakeCipher(2))
        assertNull(other.refreshToken())
        assertTrue(other.refreshTokenMissing)
    }

    @Test
    fun anEmptyTokenIsRefused() = runBlocking {
        assertFailsWith<IllegalArgumentException> { open().saveRefreshToken("") }
        Unit
    }

    @Test
    fun theInMemoryStoreForgetsOnClear() = runBlocking {
        val store = InMemorySessionStore()
        store.accessToken = "a"
        store.saveRefreshToken("r")
        assertEquals("r", store.refreshToken())
        store.clear()
        assertNull(store.accessToken)
        assertNull(store.refreshToken())
    }

    /** XOR with [key] and a check byte: decrypting with another key fails, like AES-GCM. */
    private class FakeCipher(private val key: Int) : TokenCipher {
        override fun encrypt(plain: ByteArray): ByteArray =
            byteArrayOf(key.toByte()) + plain.map { (it.toInt() xor 0x5a xor key).toByte() }

        override fun decrypt(data: ByteArray): ByteArray {
            check(data.isNotEmpty() && data[0] == key.toByte()) { "Wrong key" }
            return data.drop(1).map { (it.toInt() xor 0x5a xor key).toByte() }.toByteArray()
        }
    }
}

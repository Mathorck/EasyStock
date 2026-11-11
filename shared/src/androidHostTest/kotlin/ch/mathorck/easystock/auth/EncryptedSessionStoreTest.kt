package ch.mathorck.easystock.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EncryptedSessionStoreTest {
    private val dir: File = Files.createTempDirectory("session").toFile()
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    /** A new store on the same file, as after an app restart. */
    private fun open(cipher: TokenCipher = FakeCipher(1)): EncryptedSessionStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes.forEach { s -> s.cancel() }; scopes += it }
        return EncryptedSessionStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "session.preferences_pb") }, cipher)
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

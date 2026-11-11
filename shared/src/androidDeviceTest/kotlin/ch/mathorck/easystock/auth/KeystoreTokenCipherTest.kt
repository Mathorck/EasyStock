package ch.mathorck.easystock.auth

import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real Android Keystore, with its own key (the app's key is in another process). */
class KeystoreTokenCipherTest {
    private val cipher = KeystoreTokenCipher(ALIAS)

    @AfterTest
    fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    @Test
    fun roundTrip() {
        val plain = "refresh-token-é".encodeToByteArray()
        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)
        // A new IV each time
        assertFalse(first.contentEquals(second))
        assertEquals(plain.toList(), cipher.decrypt(first).toList())
        assertEquals(plain.toList(), KeystoreTokenCipher(ALIAS).decrypt(second).toList())
    }

    @Test
    fun aChangedByteOrAnotherKeyFails() {
        val data = cipher.encrypt("token".encodeToByteArray())
        val tampered = data.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFails { cipher.decrypt(tampered) }
        assertFails { cipher.decrypt(data.copyOf(12)) }
        deleteKey()
        assertFails { cipher.decrypt(data) }
        assertTrue(cipher.encrypt(byteArrayOf(1)).size > 12)
    }

    private companion object {
        const val ALIAS = "easystock_session_test"
    }
}

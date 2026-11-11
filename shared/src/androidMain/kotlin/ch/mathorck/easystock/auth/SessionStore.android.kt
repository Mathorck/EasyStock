package ch.mathorck.easystock.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** File of the session, in `filesDir/datastore`. Holds the encrypted refresh token only. */
const val SESSION_FILE = "session.preferences_pb"

/**
 * AES-256-GCM with a key that never leaves the Android Keystore. The output is the 12-byte IV
 * followed by the ciphertext and the tag. The key is not in the Android backup: a restored session
 * file can't be decrypted and is dropped.
 */
class KeystoreTokenCipher(private val alias: String = KEY_ALIAS) : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_SIZE) { "Truncated token" }
        val key = existingKey() ?: throw IllegalStateException("No session key")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    @Synchronized
    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "easystock_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

@Volatile
private var instance: SessionStore? = null

/** Process-wide session store (DataStore allows one instance per file). */
fun sessionStore(context: Context): SessionStore =
    instance ?: synchronized(SessionStore::class) {
        instance ?: EncryptedSessionStore(
            PreferenceDataStoreFactory.create {
                File(context.applicationContext.filesDir, "datastore/$SESSION_FILE")
            },
            KeystoreTokenCipher(),
        ).also { instance = it }
    }

@Composable
actual fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { sessionStore(context) }
}

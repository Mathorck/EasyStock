package ch.mathorck.easystock.auth

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64

/**
 * The tokens of the server session (server mode only, not used yet).
 * - The access token (short-lived) stays in memory only: lost when the app stops, the next request
 *   gets a new one with the refresh token.
 * - The refresh token is kept across restarts, encrypted.
 * Standalone mode never creates a session.
 */
interface SessionStore {
    /** In memory only, null when not logged in or after a restart. */
    var accessToken: String?

    suspend fun refreshToken(): String?

    suspend fun saveRefreshToken(token: String)

    /** Logout, leaving the server, or reset of all data: forgets both tokens. */
    suspend fun clear()
}

/** Encrypts the refresh token with a key the platform keeps out of the app files (Android Keystore). */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws when the data was not made by this key (key lost, file restored from another phone). */
    fun decrypt(data: ByteArray): ByteArray
}

/**
 * [SessionStore] in a small DataStore file, the refresh token encrypted by [cipher].
 * A token that can't be decrypted is deleted: the user logs in again.
 */
class EncryptedSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : SessionStore {
    @Volatile
    override var accessToken: String? = null

    override suspend fun refreshToken(): String? {
        val stored = dataStore.data.first()[REFRESH_TOKEN] ?: return null
        val token = runCatching { cipher.decrypt(Base64.decode(stored)).decodeToString(throwOnInvalidSequence = true) }
            .getOrNull()
        if (token.isNullOrEmpty()) {
            dataStore.edit { it.remove(REFRESH_TOKEN) }
            return null
        }
        return token
    }

    override suspend fun saveRefreshToken(token: String) {
        require(token.isNotEmpty()) { "Empty refresh token" }
        val encrypted = Base64.encode(cipher.encrypt(token.encodeToByteArray()))
        dataStore.edit { it[REFRESH_TOKEN] = encrypted }
    }

    override suspend fun clear() {
        accessToken = null
        dataStore.edit { it.remove(REFRESH_TOKEN) }
    }

    private companion object {
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}

/** Both tokens in memory only: lost when the app stops. */
class InMemorySessionStore : SessionStore {
    @Volatile
    override var accessToken: String? = null

    @Volatile
    private var refresh: String? = null

    override suspend fun refreshToken(): String? = refresh

    override suspend fun saveRefreshToken(token: String) {
        require(token.isNotEmpty()) { "Empty refresh token" }
        refresh = token
    }

    override suspend fun clear() {
        accessToken = null
        refresh = null
    }
}

/** The session store of this phone, one per process (an in-memory stub on iOS for now). */
@Composable
expect fun rememberSessionStore(): SessionStore

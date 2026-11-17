package ch.mathorck.easystock.services.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first

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

    @Volatile
    override var refreshTokenMissing: Boolean = false
        private set

    override suspend fun refreshToken(): String? {
        val stored = dataStore.data.first()[REFRESH_TOKEN]
        if (stored == null) {
            refreshTokenMissing = true
            return null
        }
        val token = runCatching { cipher.decrypt(Base64.decode(stored)).decodeToString(throwOnInvalidSequence = true) }
            .getOrNull()
        if (token.isNullOrEmpty()) {
            dataStore.edit { it.remove(REFRESH_TOKEN) }
            refreshTokenMissing = true
            return null
        }
        refreshTokenMissing = false
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

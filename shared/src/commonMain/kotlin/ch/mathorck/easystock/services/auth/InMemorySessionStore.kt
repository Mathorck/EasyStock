package ch.mathorck.easystock.services.auth

import kotlin.concurrent.Volatile

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

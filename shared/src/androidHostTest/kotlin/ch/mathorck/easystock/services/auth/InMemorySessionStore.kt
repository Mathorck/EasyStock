package ch.mathorck.easystock.services.auth

/** The tokens of one device session: the refresh token rotates, the clients of the session share it. */
class InMemorySessionStore : SessionStore {
    @Volatile override var accessToken: String? = null

    @Volatile private var refresh: String? = null

    override suspend fun refreshToken(): String? = refresh

    override suspend fun saveRefreshToken(token: String) {
        refresh = token
    }

    override suspend fun clear() {
        accessToken = null
        refresh = null
    }
}

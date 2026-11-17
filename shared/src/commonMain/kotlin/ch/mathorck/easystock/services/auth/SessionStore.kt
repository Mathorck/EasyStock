package ch.mathorck.easystock.services.auth

import androidx.compose.runtime.Composable

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

    /**
     * True when the last [refreshToken] read found no usable token on this phone (never stored, or
     * stored but unreadable, e.g. the Keystore key was lost). A session that ends then was lost locally,
     * not revoked by the server: the user is told to log in again.
     */
    val refreshTokenMissing: Boolean get() = false

    suspend fun saveRefreshToken(token: String)

    /** Logout, leaving the server, or reset of all data: forgets both tokens. */
    suspend fun clear()
}

/** The session store of this phone, one per process (an in-memory stub on iOS for now). */
@Composable
expect fun rememberSessionStore(): SessionStore

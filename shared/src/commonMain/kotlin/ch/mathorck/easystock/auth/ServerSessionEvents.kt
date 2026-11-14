package ch.mathorck.easystock.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * Process-wide events of the server session. The HTTP client reports a revoked session here (from inside
 * its token refresh, where the wipe can't run: the wipe closes that client); MainComposable runs the wipe
 * and tells the user.
 */
object ServerSessionEvents {
    private val _revoked = MutableStateFlow(false)

    /** True until the wipe after a revoked session is done. */
    val revoked: StateFlow<Boolean> = _revoked.asStateFlow()

    /** Set while the user logs out: a 401 then is not reported as revoked, the logout wipes anyway. */
    @Volatile
    var leaving: Boolean = false

    /**
     * Why the last session ended: true when this phone lost its own token (unreadable or missing), not a
     * revoke by the server. The user then logs in again; the wipe is the same.
     */
    @Volatile
    var lostLocally: Boolean = false
        private set

    /** [lostLocally]: the token was missing or unreadable on this phone, no server answer. */
    fun sessionRevoked(lostLocally: Boolean = false) {
        if (leaving) return
        // Once the server revoked it, a later read of the (now cleared) token doesn't change the cause
        if (!_revoked.value) this.lostLocally = lostLocally
        _revoked.value = true
    }

    fun revokedHandled() {
        _revoked.value = false
        lostLocally = false
    }
}

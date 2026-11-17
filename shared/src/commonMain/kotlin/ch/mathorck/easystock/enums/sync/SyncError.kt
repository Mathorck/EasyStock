package ch.mathorck.easystock.enums.sync

/** Why the last cycle failed, as the Account screen shows it. */
enum class SyncError {
    /** No answer: offline or server down. Retried at the next trigger. */
    NETWORK,

    /**
     * No answer while Android blocks the local network for this app (Android 17+, permission not granted).
     * Only the user can fix it (server/LocalNetworkAccess): the screens ask for it.
     */
    LOCAL_NETWORK_DENIED,

    /** 426: the server needs a newer app. Sync is paused until the app is updated (restarted). */
    APP_TOO_OLD,

    /** The session was revoked: the phone is wiped (auth/ServerSessionEvents). */
    SESSION_REVOKED,

    /** 403 on a push: the role can't send changes any more. */
    FORBIDDEN,

    /** Any other refusal or an answer the app can't read. */
    SERVER,
}

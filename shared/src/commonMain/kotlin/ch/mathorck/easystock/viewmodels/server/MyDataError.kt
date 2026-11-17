package ch.mathorck.easystock.viewmodels.server

/** Why "Download my data" failed. */
enum class MyDataError {
    NETWORK,

    /** 429: 5 exports per hour. */
    RATE_LIMITED,

    /** 401: the session ended (the revoke handler wipes the phone). */
    SESSION_REVOKED,

    /** The server needs a newer app. */
    APP_TOO_OLD,
    SERVER,

    /** The file could not be written, saved or shared on this phone. */
    FILE,
}

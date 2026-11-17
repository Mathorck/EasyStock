package ch.mathorck.easystock.enums.server

/** What the connect screen tells the user. */
enum class ConnectError {
    /** No answer: offline, wrong address, server down, certificate refused. */
    NETWORK,

    /** No answer while Android blocks the local network for this app (Android 17+, permission not granted). */
    LOCAL_NETWORK_DENIED,

    /** The server's certificate was refused ([ApiException.tlsFailure]): plain HTTP is never offered after it. */
    TLS,

    /** The address answers, but not as an EasyStock server (no `/server-info`). */
    NOT_A_SERVER,

    /** The server speaks another API version. */
    API_UNSUPPORTED,

    /** The server needs a newer app (426 or a higher minimum version). */
    APP_TOO_OLD,

    /** Setup code, but the server already has its first Admin. */
    SETUP_DONE,

    /** Login or invite on a server without its first Admin yet. */
    NOT_SET_UP,
    WRONG_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,

    /** Unknown invite, printed, setup or reset code. */
    CODE_INVALID,
    CODE_EXPIRED,
    CODE_USED,
    GUEST_SIGNUP_OFF,
    GUEST_LIMIT_REACHED,
    USERNAME_TAKEN,

    /** A field the server refused (validation). */
    INVALID,
    RATE_LIMITED,

    /** Logout or promotion: the session is no longer valid. */
    SESSION_REVOKED,

    /** No pending promotion (any more). */
    NO_PROMOTION,

    /** The own account can't be deleted: the user is the last active Admin (409 `last-admin`). */
    LAST_ADMIN,
    SERVER,

    /** Writing the connection on this phone failed. */
    LOCAL,
}

package ch.mathorck.easystock.enums.server

/** What went wrong with a server call, as the UI shows it. */
enum class ApiError {
    /** No answer: offline, server down, timeout, certificate refused. */
    NETWORK,

    /** 401: the session was revoked, expired or the account disabled. The user must connect again. */
    SESSION_REVOKED,

    /** 426: the server needs a newer app (or another schema version). */
    APP_TOO_OLD,

    /** 403: the role is not allowed to do this. */
    FORBIDDEN,

    /** 404. */
    NOT_FOUND,

    /** 409: refused because of the current state (last Admin, Guest limit...), see [ApiException.problemType]. */
    CONFLICT,

    /** 400, 410, 413, 415, 422: the request was refused as invalid or expired, see [ApiException.problemType]. */
    INVALID,

    /** 429: too many requests, try again later. */
    RATE_LIMITED,

    /** 5xx, or an answer the app can't read. */
    SERVER,
}

/** The error of an HTTP status. */
internal fun apiErrorOf(status: Int): ApiError = when {
    // A 401 left after the refresh retry (session-revoked, account-disabled, unauthorized...):
    // the session can't be used any more
    status == 401 -> ApiError.SESSION_REVOKED
    status == 426 -> ApiError.APP_TOO_OLD
    status == 403 -> ApiError.FORBIDDEN
    status == 404 -> ApiError.NOT_FOUND
    status == 409 -> ApiError.CONFLICT
    status == 429 -> ApiError.RATE_LIMITED
    status in 400..499 -> ApiError.INVALID
    else -> ApiError.SERVER
}

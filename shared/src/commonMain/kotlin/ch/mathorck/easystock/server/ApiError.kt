package ch.mathorck.easystock.server

import kotlinx.serialization.Serializable

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

/**
 * A failed server call. [problemType] is the stable `type` of the RFC 9457 Problem Details answer
 * (`session-revoked`, `last-admin`...), when the server sent one. Never carries tokens or row content.
 */
class ApiException(
    val error: ApiError,
    val status: Int? = null,
    val problemType: String? = null,
    cause: Throwable? = null,
) : Exception("Server call failed: $error${status?.let { " ($it)" } ?: ""}${problemType?.let { " $it" } ?: ""}", cause)

/** RFC 9457 Problem Details, as sent by the server for every error. */
@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
)

/** Problem `type` values the app reacts to (docs: 03-server-spec.md, 04 plan §3.6). */
object ProblemTypes {
    const val SESSION_REVOKED = "session-revoked"
    const val ACCOUNT_DISABLED = "account-disabled"
    const val APP_TOO_OLD = "app-too-old"
    const val NOT_ALLOWED = "not-allowed"
    const val LAST_ADMIN = "last-admin"
    const val ROLE_NOT_ALLOWED = "role-not-allowed"
    const val USE_PROMOTION = "use-promotion"
    const val SHORT_CODE_MISMATCH = "short-code-mismatch"
    const val GUEST_LIMIT_REACHED = "guest-limit-reached"
    const val BACKUP_TOO_LARGE = "backup-too-large"
    const val DATASET_RESET = "dataset-reset"
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

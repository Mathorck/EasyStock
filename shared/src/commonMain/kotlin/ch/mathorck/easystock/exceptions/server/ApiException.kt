package ch.mathorck.easystock.exceptions.server

import ch.mathorck.easystock.enums.server.ApiError

/**
 * A failed server call. [problemType] is the stable `type` of the RFC 9457 Problem Details answer
 * (`session-revoked`, `last-admin`...), when the server sent one. Never carries tokens or row content.
 */
class ApiException(
    val error: ApiError,
    val status: Int? = null,
    val problemType: String? = null,
    cause: Throwable? = null,
    /**
     * [ApiError.NETWORK] because the server's certificate was refused (untrusted, wrong pin, wrong host
     * name): something answered, but it could not prove it is the server. Never a reason to try plain HTTP.
     */
    val tlsFailure: Boolean = false,
) : Exception("Server call failed: $error${status?.let { " ($it)" } ?: ""}${problemType?.let { " $it" } ?: ""}", cause)

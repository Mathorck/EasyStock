package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.enums.server.ConnectError
import ch.mathorck.easystock.exceptions.server.ApiException
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal inline fun <reified T> HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

/** API versions of `/server-info` this app speaks. */
val SUPPORTED_API_VERSIONS: Set<Int> = setOf(1)

/**
 * The message of a failed join call. By the Problem Details `type` first: a 401 before joining is a
 * wrong password, not a revoked session ([apiErrorOf] maps every 401 to [ApiError.SESSION_REVOKED]).
 */
fun connectErrorOf(e: ApiException): ConnectError = when (e.problemType) {
    ProblemTypes.INVALID_CREDENTIALS -> ConnectError.WRONG_CREDENTIALS
    ProblemTypes.ACCOUNT_LOCKED -> ConnectError.ACCOUNT_LOCKED
    ProblemTypes.ACCOUNT_DISABLED -> ConnectError.ACCOUNT_DISABLED
    ProblemTypes.INVITE_INVALID -> ConnectError.CODE_INVALID
    ProblemTypes.INVITE_EXPIRED, ProblemTypes.RESET_EXPIRED, ProblemTypes.PROMOTION_EXPIRED -> ConnectError.CODE_EXPIRED
    ProblemTypes.INVITE_USED, ProblemTypes.RESET_USED -> ConnectError.CODE_USED
    ProblemTypes.SETUP_DONE -> ConnectError.SETUP_DONE
    ProblemTypes.USERNAME_TAKEN -> ConnectError.USERNAME_TAKEN
    ProblemTypes.GUEST_SIGNUP_OFF -> ConnectError.GUEST_SIGNUP_OFF
    ProblemTypes.GUEST_LIMIT_REACHED -> ConnectError.GUEST_LIMIT_REACHED
    ProblemTypes.APP_TOO_OLD -> ConnectError.APP_TOO_OLD
    ProblemTypes.SESSION_REVOKED -> ConnectError.SESSION_REVOKED
    ProblemTypes.NO_PROMOTION -> ConnectError.NO_PROMOTION
    ProblemTypes.LAST_ADMIN -> ConnectError.LAST_ADMIN
    ProblemTypes.VALIDATION_FAILED -> ConnectError.INVALID
    ProblemTypes.RATE_LIMITED -> ConnectError.RATE_LIMITED
    else -> when (e.error) {
        ApiError.NETWORK -> if (e.tlsFailure) ConnectError.TLS else ConnectError.NETWORK
        ApiError.APP_TOO_OLD -> ConnectError.APP_TOO_OLD
        ApiError.RATE_LIMITED -> ConnectError.RATE_LIMITED
        // Without a type: a 401 to a login is still a wrong password
        ApiError.SESSION_REVOKED -> ConnectError.WRONG_CREDENTIALS
        ApiError.NOT_FOUND -> ConnectError.CODE_INVALID
        ApiError.CONFLICT, ApiError.INVALID, ApiError.FORBIDDEN -> ConnectError.INVALID
        ApiError.SERVER -> ConnectError.SERVER
    }
}

/**
 * A call without answer while the system blocks the local network for this app ([localNetworkMissing],
 * see [LocalNetworkAccess]): the missing permission is the likely cause, not the server.
 */
fun ConnectError.withLocalNetwork(localNetworkMissing: Boolean): ConnectError =
    if (this == ConnectError.NETWORK && localNetworkMissing) ConnectError.LOCAL_NETWORK_DENIED else this

/**
 * Checks the `/server-info` answer: null when the app can talk to this server, else why not.
 * Only the API version and the minimum app version are checked here; an unsupported schema version is
 * answered 426 by the server at the first call that carries the headers.
 */
fun checkServerInfo(info: ServerInfo, appVersion: String): ConnectError? = when {
    info.apiVersion !in SUPPORTED_API_VERSIONS -> ConnectError.API_UNSUPPORTED
    info.minAppVersion.isNotBlank() && compareVersions(appVersion, info.minAppVersion) < 0 -> ConnectError.APP_TOO_OLD
    else -> null
}

/**
 * Compares `major.minor.patch` versions, ignoring a `-beta` or `+build` suffix; a missing or unreadable
 * part counts as 0.
 */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.substringBefore('-').substringBefore('+').split('.').map { it.trim().toIntOrNull() ?: 0 }
    val x = parts(a)
    val y = parts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

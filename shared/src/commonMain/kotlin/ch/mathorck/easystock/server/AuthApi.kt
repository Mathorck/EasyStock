package ch.mathorck.easystock.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * The calls made before joining a server, without a token. Every call throws [ApiException] on failure;
 * [connectErrorOf] tells the user what went wrong.
 */
interface JoinApi {
    suspend fun serverInfo(): ServerInfo

    suspend fun login(request: LoginRequest): TokenAnswer

    suspend fun setup(request: SetupRequest): TokenAnswer

    suspend fun redeemInvite(request: RedeemInviteRequest): TokenAnswer

    suspend fun guestSignup(request: GuestSignupRequest): TokenAnswer

    suspend fun resetPassword(request: ResetPasswordRequest): TokenAnswer
}

/** [JoinApi] over a [createPublicClient] client. */
class KtorJoinApi(private val http: HttpClient) : JoinApi, AutoCloseable {
    override suspend fun serverInfo(): ServerInfo = apiCall { http.get("server-info").body() }

    override suspend fun login(request: LoginRequest): TokenAnswer = post("auth/login", request)

    override suspend fun setup(request: SetupRequest): TokenAnswer = post("auth/setup", request)

    override suspend fun redeemInvite(request: RedeemInviteRequest): TokenAnswer = post("auth/invite/redeem", request)

    override suspend fun guestSignup(request: GuestSignupRequest): TokenAnswer = post("auth/guest-signup", request)

    override suspend fun resetPassword(request: ResetPasswordRequest): TokenAnswer = post("auth/reset", request)

    private suspend inline fun <reified T> post(path: String, body: T): TokenAnswer = apiCall {
        http.post(path) { json(body) }.body()
    }

    override fun close() = http.close()
}

/** The calls of the Account screen, with the session of the connected server. */
interface AccountApi {
    suspend fun me(): MeAnswer

    /** `PATCH /me` `{"alertsEnabled": enabled}`: the alert preference (Guests cannot). */
    suspend fun setAlertsEnabled(enabled: Boolean)

    /** `PATCH /me` `{"displayName": name}`: answers the updated account. */
    suspend fun setDisplayName(name: String): MeAnswer

    /**
     * `POST /me/password` (full accounts only). The server ends every other session of the user and keeps
     * this one, so the tokens of this phone stay valid.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    /** Revokes this session on the server; a Guest account is deleted. */
    suspend fun logout()

    /** A Guest with a pending promotion chooses a username and a password. Returns new tokens. */
    suspend fun completePromotion(request: CompletePromotionRequest): TokenAnswer
}

/** [AccountApi] over the shared [ServerClient]. */
class KtorAccountApi(private val client: ServerClient) : AccountApi {
    private val http get() = client.http

    override fun equals(other: Any?): Boolean = other is KtorAccountApi && other.client === client

    override fun hashCode(): Int = client.hashCode()

    override suspend fun me(): MeAnswer = apiCall { http.get("me").body() }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        apiCall { http.patch("me") { json(UpdateAlertsRequest(enabled)) } }
    }

    override suspend fun setDisplayName(name: String): MeAnswer =
        apiCall { http.patch("me") { json(UpdateDisplayNameRequest(name)) }.body() }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        apiCall { http.post("me/password") { json(ChangePasswordRequest(currentPassword, newPassword)) } }
    }

    override suspend fun logout() {
        apiCall { http.post("auth/logout") }
    }

    override suspend fun completePromotion(request: CompletePromotionRequest): TokenAnswer =
        apiCall { http.post("auth/complete-promotion") { json(request) }.body() }
}

private inline fun <reified T> HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

/** API versions of `/server-info` this app speaks. */
val SUPPORTED_API_VERSIONS: Set<Int> = setOf(1)

/** What the connect screen tells the user. */
enum class ConnectError {
    /** No answer: offline, wrong address, server down, certificate refused. */
    NETWORK,

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
    SERVER,

    /** Writing the connection on this phone failed. */
    LOCAL,
}

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
    ProblemTypes.VALIDATION_FAILED -> ConnectError.INVALID
    ProblemTypes.RATE_LIMITED -> ConnectError.RATE_LIMITED
    else -> when (e.error) {
        ApiError.NETWORK -> ConnectError.NETWORK
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

package ch.mathorck.easystock.server

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.auth.SessionStore
import ch.mathorck.easystock.data.db.DB_VERSION
import ch.mathorck.easystock.data.model.SyncState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The server of the `sync_state` row: what the HTTP client needs. */
data class ServerConnection(
    /** Without a trailing slash, e.g. `https://stock.example.org` or `http://192.168.1.20:8080`. */
    val serverUrl: String,
    /** SHA-256 of the self-signed certificate (64 lowercase hex), null for HTTP or a trusted certificate. */
    val fingerprint: String? = null,
) {
    val isHttps: Boolean get() = serverUrl.startsWith("https://")

    /** Base of every API call. */
    val apiBase: String get() = serverUrl.trimEnd('/') + "/api/v1/"

    companion object {
        fun of(state: SyncState) = ServerConnection(state.serverUrl.trimEnd('/'), state.certFingerprint)
    }
}

/** Headers sent with every request, so the server can refuse a too old app (426). */
data class ClientVersions(
    /** App version name, e.g. `1.4.0`. */
    val appVersion: String,
    /** Database schema version of this app ([DB_VERSION]). */
    val schemaVersion: Int = DB_VERSION,
) {
    companion object {
        const val APP_HEADER = "X-EasyStock-App-Version"
        const val SCHEMA_HEADER = "X-EasyStock-Schema-Version"
    }
}

/** JSON of the API: unknown fields ignored (newer server), null fields not sent. */
val ServerJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}

/**
 * The HTTP client of one server connection. One per process and connection ([ServerClientHolder]), so
 * the token refreshes of all screens are shared: the server revokes a session whose old refresh token is
 * sent twice. Never created in standalone mode: it needs a `sync_state` row.
 */
class ServerClient internal constructor(
    val connection: ServerConnection,
    val http: HttpClient,
) : AutoCloseable {
    override fun close() = http.close()
}

/**
 * Builds the client of [connection] on [engine] (OkHttp with the fingerprint pin on Android, a mock in
 * tests). The access token stays in [session] memory; on a 401 it is refreshed once with the stored
 * refresh token, and the request is sent again. When the refresh is refused (401), the tokens are
 * forgotten, [onSessionRevoked] runs and the call fails with [ApiError.SESSION_REVOKED].
 */
fun createServerClient(
    connection: ServerConnection,
    engine: HttpClientEngine,
    session: SessionStore,
    versions: ClientVersions,
    onSessionRevoked: suspend () -> Unit = {},
): ServerClient {
    val http = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(ServerJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 120_000
        }
        defaultRequest {
            url(connection.apiBase)
            header(ClientVersions.APP_HEADER, versions.appVersion)
            header(ClientVersions.SCHEMA_HEADER, versions.schemaVersion.toString())
        }
        install(Auth) {
            bearer {
                // The token can change outside this client (login, logout): read it for every request
                cacheTokens = false
                loadTokens { session.accessToken?.let { BearerTokens(it, null) } }
                // Send the token right away, without waiting for a 401 first (never on the refresh call: it is marked)
                sendWithoutRequest { true }
                refreshTokens {
                    val refresh = session.refreshToken()
                        ?: revoked(session, onSessionRevoked)
                    val response: HttpResponse = client.post("auth/refresh") {
                        markAsRefreshTokenRequest()
                        this.expectSuccess = false
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refresh))
                    }
                    when {
                        response.status.isSuccess() -> {
                            val answer = response.body<TokenAnswer>()
                            session.saveRefreshToken(answer.refreshToken)
                            session.accessToken = answer.accessToken
                            BearerTokens(answer.accessToken, answer.refreshToken)
                        }
                        response.status.value == 401 -> revoked(session, onSessionRevoked)
                        else -> throw response.toApiException()
                    }
                }
            }
        }
    }
    return ServerClient(connection, http)
}

private suspend fun revoked(session: SessionStore, onSessionRevoked: suspend () -> Unit): Nothing {
    session.clear()
    onSessionRevoked()
    throw ApiException(ApiError.SESSION_REVOKED, 401, ProblemTypes.SESSION_REVOKED)
}

/** Reads the Problem Details of a failed answer. */
internal suspend fun HttpResponse.toApiException(cause: Throwable? = null): ApiException {
    val type = runCatching { ServerJson.decodeFromString<ProblemDetails>(bodyAsText()).type }.getOrNull()
    return ApiException(apiErrorOf(status.value), status.value, type, cause)
}

/**
 * Runs one API call and turns every failure into an [ApiException]: HTTP errors with their Problem
 * Details type, network failures as [ApiError.NETWORK], unreadable answers as [ApiError.SERVER].
 */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: ApiException) {
    throw e
} catch (e: CancellationException) {
    throw e
} catch (e: ResponseException) {
    throw e.response.toApiException(e)
} catch (e: IOException) {
    throw ApiException(ApiError.NETWORK, cause = e)
} catch (e: SerializationException) {
    throw ApiException(ApiError.SERVER, cause = e)
} catch (e: ContentConvertException) {
    throw ApiException(ApiError.SERVER, cause = e)
} catch (e: IllegalArgumentException) {
    // A body Ktor could not convert (wrong content type...)
    throw ApiException(ApiError.SERVER, cause = e)
}

/**
 * The client of the current connection, rebuilt when the server changes and closed when it is replaced
 * or [release]d. [create] runs only when [clientFor] is called, so only with a `sync_state` row.
 * Used from the main thread (composition).
 */
class ServerClientHolder(private val create: (ServerConnection) -> ServerClient) {
    private var current: ServerClient? = null

    fun clientFor(connection: ServerConnection): ServerClient =
        current?.takeIf { it.connection == connection } ?: create(connection).also {
            current?.close()
            current = it
        }

    /** Leaving the server: closes the client. */
    fun release() {
        current?.close()
        current = null
    }
}

/** The platform HTTP engine: OkHttp on Android, pinned to [fingerprint] when there is one. */
expect fun serverHttpEngine(fingerprint: String?): HttpClientEngine

/**
 * The client of the connected server, null in standalone mode ([state] null): no client, no network.
 * Process-wide, see [ServerClient].
 */
@Composable
expect fun rememberServerClient(state: SyncState?): ServerClient?

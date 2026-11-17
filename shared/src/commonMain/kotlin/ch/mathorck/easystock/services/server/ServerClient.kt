package ch.mathorck.easystock.services.server

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.data.dto.server.ProblemDetails
import ch.mathorck.easystock.data.dto.server.RefreshRequest
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.enums.server.apiErrorOf
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.auth.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.NoTransformationFoundException
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
import kotlinx.coroutines.Job
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
    val http = ownedClient(engine) {
        baseConfig(connection, versions)
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

/** JSON, timeouts, base URL and version headers: what every server client has. */
private fun HttpClientConfig<*>.baseConfig(connection: ServerConnection, versions: ClientVersions) {
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
}

/**
 * A client without any token, for the calls made before joining (`/server-info`, login, setup, invite,
 * Guest signup, reset). No bearer plugin: a 401 (wrong password) is an answer, never a revoked
 * session, so it can't trigger the wipe of [createServerClient].
 */
fun createPublicClient(connection: ServerConnection, engine: HttpClientEngine, versions: ClientVersions): HttpClient =
    ownedClient(engine) { baseConfig(connection, versions) }

/**
 * A client that owns [engine]: closing the client closes the engine (its OkHttp connection pool and
 * threads) once the running calls are done. `HttpClient(engine) {}` alone leaves an engine passed in
 * open, so each replaced client would leak one.
 */
private fun ownedClient(engine: HttpClientEngine, block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(engine, block).also { client ->
        client.coroutineContext[Job]!!.invokeOnCompletion { engine.close() }
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
    throw ApiException(ApiError.NETWORK, cause = e, tlsFailure = isTlsFailure(e))
} catch (e: SerializationException) {
    throw ApiException(ApiError.SERVER, cause = e)
} catch (e: ContentConvertException) {
    throw ApiException(ApiError.SERVER, cause = e)
} catch (e: IllegalArgumentException) {
    // A body Ktor could not convert (wrong content type...)
    throw ApiException(ApiError.SERVER, cause = e)
} catch (e: NoTransformationFoundException) {
    // Not JSON at all: a web page, a proxy error... not an EasyStock answer
    throw ApiException(ApiError.SERVER, cause = e)
}

/**
 * True when [e] (or a cause) is a refused server certificate or host name: a TLS answer that could not
 * be trusted. False for a refused connection, a timeout, an unknown host, and for a TLS protocol error
 * such as a plain HTTP server on that port (the case the HTTP fallback of a schemeless address is for).
 */
internal expect fun isTlsFailure(e: Throwable): Boolean

/** The platform HTTP engine: OkHttp on Android, pinned to [fingerprint] when there is one. */
expect fun serverHttpEngine(fingerprint: String?): HttpClientEngine

/**
 * The client of the connected server, null in standalone mode ([state] null): no client, no network.
 * Process-wide, see [ServerClient].
 */
@Composable
expect fun rememberServerClient(state: SyncState?): ServerClient?

/** Leaving the server: closes the shared client of the connection. Main thread. */
expect fun releaseServerClient()

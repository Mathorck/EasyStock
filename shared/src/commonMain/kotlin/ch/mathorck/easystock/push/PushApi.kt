package ch.mathorck.easystock.push

import ch.mathorck.easystock.server.ServerClient
import ch.mathorck.easystock.server.apiCall
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

// Push endpoints, see 03-server-spec.md "Push": the server keeps one UnifiedPush address per session.

/** `POST /push/register` body. [endpoint]: the UnifiedPush address the server sends "sync" to. */
@Serializable
data class PushRegisterRequest(val endpoint: String)

/** The push calls. A fake in tests. */
interface PushApi {
    /** Saves [endpoint] for the current session (the server replaces the previous one). */
    suspend fun register(endpoint: String)

    /** Forgets the address of the current session. */
    suspend fun unregister()
}

class KtorPushApi(private val client: ServerClient) : PushApi {
    private val http get() = client.http

    override suspend fun register(endpoint: String) {
        apiCall {
            http.post("push/register") {
                contentType(ContentType.Application.Json)
                setBody(PushRegisterRequest(endpoint))
            }
        }
    }

    override suspend fun unregister() {
        apiCall { http.delete("push/register") }
    }
}

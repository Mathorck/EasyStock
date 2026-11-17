package ch.mathorck.easystock.services.push

import ch.mathorck.easystock.data.dto.push.PushRegisterRequest
import ch.mathorck.easystock.services.server.ServerClient
import ch.mathorck.easystock.services.server.apiCall
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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

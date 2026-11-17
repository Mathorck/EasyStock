package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.sync.PullAnswer
import ch.mathorck.easystock.data.dto.sync.PushAnswer
import ch.mathorck.easystock.services.server.ServerClient
import ch.mathorck.easystock.services.server.apiCall
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent

class KtorSyncApi(private val client: ServerClient) : SyncApi {
    private val http get() = client.http

    override suspend fun push(body: String, epoch: String?): PushAnswer = apiCall {
        http.post("sync/push") {
            epoch?.let { header(DATASET_EPOCH_HEADER, it) }
            setBody(TextContent(body, ContentType.Application.Json))
        }.body()
    }

    override suspend fun pull(since: Long, limit: Int, epoch: String?): PullAnswer = apiCall {
        http.get("sync/pull") {
            epoch?.let { header(DATASET_EPOCH_HEADER, it) }
            parameter("since", since)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun serverInfo(): ServerInfo = apiCall { http.get("server-info").body() }

    override suspend fun hasImage(sha256: String): Boolean = apiCall {
        val response = http.head("images/$sha256") { expectSuccess = false }
        when (response.status) {
            HttpStatusCode.OK -> true
            HttpStatusCode.NotFound -> false
            else -> throw ResponseException(response, "")
        }
    }

    override suspend fun putImage(sha256: String, jpeg: ByteArray) {
        apiCall { http.put("images/$sha256") { setBody(ByteArrayContent(jpeg, ContentType.Image.JPEG)) } }
    }

    override suspend fun getImage(sha256: String): ByteArray? = apiCall {
        val response = http.get("images/$sha256") { expectSuccess = false }
        when (response.status) {
            HttpStatusCode.OK -> response.readRawBytes()
            HttpStatusCode.NotFound -> null
            else -> throw ResponseException(response, "")
        }
    }
}

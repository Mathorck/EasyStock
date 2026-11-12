package ch.mathorck.easystock.push

import ch.mathorck.easystock.auth.InMemorySessionStore
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ClientVersions
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.createServerClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PushApiTest {
    private data class Seen(val method: HttpMethod, val path: String, val auth: String?, val contentType: String?, val body: String)

    private val seen = mutableListOf<Seen>()
    private val session = InMemorySessionStore()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) }): PushApi {
        val engine = MockEngine { request ->
            seen += Seen(
                request.method,
                request.url.encodedPath,
                request.headers[HttpHeaders.Authorization],
                request.body.contentType?.toString(),
                request.body.toByteArray().decodeToString(),
            )
            handler(request)
        }
        val client = createServerClient(ServerConnection("http://192.168.1.20:8080"), engine, session, ClientVersions("1.2.3", schemaVersion = 10))
        return KtorPushApi(client)
    }

    @Test
    fun registerPostsTheEndpointWithTheToken() = runTest {
        session.accessToken = "access-1"
        api().register("https://ntfy.example.org/upAbc123?up=1")
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/push/register", request.path)
        assertEquals("Bearer access-1", request.auth)
        assertEquals("application/json", request.contentType?.substringBefore(';'))
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals(setOf("endpoint"), body.keys)
        assertEquals("https://ntfy.example.org/upAbc123?up=1", body.getValue("endpoint").jsonPrimitive.content)
    }

    @Test
    fun unregisterDeletesWithoutBody() = runTest {
        session.accessToken = "access-1"
        api().unregister()
        val request = seen.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/push/register", request.path)
        assertEquals("Bearer access-1", request.auth)
        assertEquals("", request.body)
    }

    @Test
    fun aRefusedRegistrationFails() = runTest {
        session.accessToken = "access-1"
        val api = api { respond("""{"type":"invalid"}""", HttpStatusCode.BadRequest) }
        assertFailsWith<ApiException> { api.register("not a url") }
    }
}

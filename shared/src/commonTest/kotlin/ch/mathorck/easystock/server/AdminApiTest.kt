package ch.mathorck.easystock.server

import ch.mathorck.easystock.auth.InMemorySessionStore
import ch.mathorck.easystock.auth.Role
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminApiTest {
    private val base = "http://192.168.1.20:8080"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** A request as the server saw it. */
    private data class Seen(val method: HttpMethod, val path: String, val query: String, val headers: Map<String, String?>, val body: String)

    private val seen = mutableListOf<Seen>()
    private val session = InMemorySessionStore()
    private var revokedCalls = 0

    private suspend fun record(request: HttpRequestData): Seen {
        val body = when (val content = request.body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readByteArray().decodeToString()
            else -> request.body.toByteArray().decodeToString()
        }
        return Seen(
            request.method,
            request.url.encodedPath,
            request.url.encodedQuery,
            listOf(HttpHeaders.Authorization, ClientVersions.APP_HEADER, ClientVersions.SCHEMA_HEADER)
                .associateWith { request.headers[it] } +
                (HttpHeaders.ContentType to (request.body.contentType?.toString() ?: request.headers[HttpHeaders.ContentType])),
            body,
        ).also { seen += it }
    }

    private fun api(handler: suspend MockRequestHandleScope.(Seen) -> HttpResponseData): AdminApi {
        val engine = MockEngine { request -> handler(record(request)) }
        val client = createServerClient(
            ServerConnection(base),
            engine,
            session,
            ClientVersions("1.2.3", schemaVersion = 9),
            onSessionRevoked = { revokedCalls++ },
        )
        return KtorAdminApi(client)
    }

    private suspend fun login() {
        session.accessToken = "access-1"
        session.saveRefreshToken("refresh-1")
    }

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK, json)
    private fun MockRequestHandleScope.noContent() = respond("", HttpStatusCode.NoContent)
    private fun body(seen: Seen): JsonElement = Json.parseToJsonElement(seen.body)

    // --- Headers, auth, errors ---

    @Test
    fun sendsTheVersionHeadersAndTheBearerToken() = runTest {
        login()
        api { ok("[]") }.users()
        val request = seen.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/admin/users", request.path)
        assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
        assertEquals("1.2.3", request.headers[ClientVersions.APP_HEADER])
        assertEquals("9", request.headers[ClientVersions.SCHEMA_HEADER])
    }

    @Test
    fun refreshesOn401ThenRetries() = runTest {
        login()
        val api = api { request ->
            when {
                request.path.endsWith("/auth/refresh") -> ok("""{"accessToken":"access-2","refreshToken":"refresh-2"}""")
                request.headers[HttpHeaders.Authorization] == "Bearer access-1" ->
                    respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
                else -> ok("[]")
            }
        }
        assertEquals(emptyList(), api.users())
        assertEquals(listOf("/api/v1/admin/users", "/api/v1/auth/refresh", "/api/v1/admin/users"), seen.map { it.path })
        val refresh = seen[1]
        assertEquals(HttpMethod.Post, refresh.method)
        assertEquals("refresh-1", body(refresh).jsonObject["refreshToken"]!!.jsonPrimitive.content)
        assertNull(refresh.headers[HttpHeaders.Authorization])
        assertEquals("1.2.3", refresh.headers[ClientVersions.APP_HEADER])
        assertEquals("Bearer access-2", seen[2].headers[HttpHeaders.Authorization])
        // The rotated refresh token replaces the old one, the access token stays in memory
        assertEquals("refresh-2", session.refreshToken())
        assertEquals("access-2", session.accessToken)
        assertEquals(0, revokedCalls)
    }

    @Test
    fun refreshesOn401WithoutAWwwAuthenticateHeader() = runTest {
        login()
        val api = api { request ->
            when {
                request.path.endsWith("/auth/refresh") -> ok("""{"accessToken":"access-2","refreshToken":"refresh-2"}""")
                request.headers[HttpHeaders.Authorization] == "Bearer access-1" -> respond("", HttpStatusCode.Unauthorized)
                else -> ok("[]")
            }
        }
        api.users()
        assertEquals(3, seen.size)
        assertEquals("refresh-2", session.refreshToken())
    }

    @Test
    fun aRefusedRefreshRevokesTheSession() = runTest {
        login()
        val api = api { request ->
            if (request.path.endsWith("/auth/refresh")) {
                respond("""{"type":"session-revoked","status":401}""", HttpStatusCode.Unauthorized, json)
            } else {
                respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
            }
        }
        val e = assertFailsWith<ApiException> { api.users() }
        assertEquals(ApiError.SESSION_REVOKED, e.error)
        assertEquals(1, revokedCalls)
        assertNull(session.accessToken)
        assertNull(session.refreshToken())
    }

    @Test
    fun noRefreshTokenRevokesTheSessionWithoutARefreshCall() = runTest {
        session.accessToken = "access-1"
        val api = api { respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer")) }
        assertEquals(ApiError.SESSION_REVOKED, assertFailsWith<ApiException> { api.users() }.error)
        assertTrue(seen.none { it.path.endsWith("/auth/refresh") })
        assertEquals(1, revokedCalls)
    }

    @Test
    fun mapsAppTooOld() = runTest {
        login()
        val api = api { respond("""{"type":"app-too-old","status":426}""", HttpStatusCode.UpgradeRequired, json) }
        val e = assertFailsWith<ApiException> { api.users() }
        assertEquals(ApiError.APP_TOO_OLD, e.error)
        assertEquals(426, e.status)
        assertEquals(ProblemTypes.APP_TOO_OLD, e.problemType)
    }

    @Test
    fun mapsForbidden() = runTest {
        login()
        val api = api { respond("""{"type":"not-allowed","status":403}""", HttpStatusCode.Forbidden, json) }
        val e = assertFailsWith<ApiException> { api.invites() }
        assertEquals(ApiError.FORBIDDEN, e.error)
        assertEquals(ProblemTypes.NOT_ALLOWED, e.problemType)
    }

    @Test
    fun mapsAConflictWithItsProblemType() = runTest {
        login()
        val api = api { respond("""{"type":"last-admin","status":409}""", HttpStatusCode.Conflict, json) }
        val e = assertFailsWith<ApiException> { api.changeRole("u1", Role.VIEWER) }
        assertEquals(ApiError.CONFLICT, e.error)
        assertEquals(ProblemTypes.LAST_ADMIN, e.problemType)
    }

    @Test
    fun mapsAServerErrorWithoutProblemDetails() = runTest {
        login()
        val api = api { respond("oops", HttpStatusCode.InternalServerError) }
        assertEquals(ApiError.SERVER, assertFailsWith<ApiException> { api.users() }.error)
    }

    @Test
    fun mapsANetworkFailure() = runTest {
        login()
        val api = api { throw IOException("unreachable") }
        assertEquals(ApiError.NETWORK, assertFailsWith<ApiException> { api.users() }.error)
    }

    @Test
    fun mapsAnUnreadableAnswer() = runTest {
        login()
        val api = api { ok("""{"not":"a list"}""") }
        assertEquals(ApiError.SERVER, assertFailsWith<ApiException> { api.users() }.error)
    }

    // --- Every admin call ---

    @Test
    fun serverInfo() = runTest {
        login()
        val info = api { ok("""{"serverName":"Atelier","apiVersion":1,"supportedSchemaVersions":[9],"extra":true}""") }.serverInfo()
        assertEquals("/api/v1/server-info", seen.single().path)
        assertEquals("Atelier", info.serverName)
        assertEquals(listOf(9), info.supportedSchemaVersions)
    }

    @Test
    fun users() = runTest {
        login()
        val users = api {
            ok(
                """[
                {"id":"u1","displayName":"Ana","username":"ana","role":"admin","kind":"full","shortCode":"",
                 "createdAt":"2026-11-01T08:00:00+00:00","lastSeenAt":"2026-11-10T08:00:00+00:00","disabled":false,
                 "devices":[{"id":"d1","deviceName":"Pixel","lastUsedAt":"2026-11-10T08:00:00+00:00"}],"pendingPromotion":null},
                {"id":"u2","displayName":"Guest 1","username":null,"role":"guest","kind":"guest","shortCode":"K7-4QX",
                 "createdAt":"2026-11-02T08:00:00+00:00","lastSeenAt":null,"disabled":false,"devices":[],
                 "pendingPromotion":{"role":"member","expiresAt":"2026-11-12T08:00:00+00:00"}},
                {"id":"u3","displayName":"Marc","username":"marc","role":"viewer","kind":"full","shortCode":"",
                 "createdAt":"2026-11-03T08:00:00+00:00","lastSeenAt":null,"disabled":true,"devices":[],"pendingPromotion":null}
                ]""",
            )
        }.users()
        assertEquals(3, users.size)
        assertEquals("ana", users[0].username)
        assertFalse(users[0].disabled)
        assertNull(users[1].username)
        assertEquals("marc", users[2].username)
        assertTrue(users[2].disabled)
        assertEquals(Role.ADMIN, users[0].appRole)
        assertEquals("Pixel", users[0].devices.single().deviceName)
        assertTrue(users[1].isGuest)
        assertEquals("K7-4QX", users[1].shortCode)
        assertEquals(Role.EDITOR, WireRole.parse(users[1].pendingPromotion!!.role))
    }

    @Test
    fun changeRole() = runTest {
        login()
        val user = api { ok("""{"id":"u1","role":"member"}""") }.changeRole("u1", Role.EDITOR)
        val request = seen.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/admin/users/u1", request.path)
        assertEquals("""{"role":"member"}""", request.body)
        assertTrue(request.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        assertEquals(Role.EDITOR, user.appRole)
    }

    @Test
    fun renameUserSendsOnlyTheDisplayName() = runTest {
        login()
        val user = api { ok("""{"id":"u 1","displayName":"Anna","username":"ana","role":"member","kind":"full"}""") }.rename("u 1", "Anna")
        val request = seen.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/admin/users/u%201", request.path)
        assertEquals("""{"displayName":"Anna"}""", request.body)
        assertEquals("Anna", user.displayName)
    }

    @Test
    fun renameWithAnInvalidNameIsA400() = runTest {
        login()
        val api = api { respond("""{"type":"validation-failed","status":400}""", HttpStatusCode.BadRequest, json) }
        val e = assertFailsWith<ApiException> { api.rename("u1", "") }
        assertEquals(ApiError.INVALID, e.error)
    }

    @Test
    fun disableUser() = runTest {
        login()
        api { ok("""{"id":"u1","displayName":"Ana","username":"ana","role":"member","kind":"full","shortCode":"","createdAt":"2026-11-01T08:00:00+00:00","lastSeenAt":null,"disabled":true,"devices":[],"pendingPromotion":null}""") }.setDisabled("u1", true).also { assertTrue(it.disabled) }
        assertEquals("""{"disabled":true}""", seen.single().body)
    }

    @Test
    fun deleteUser() = runTest {
        login()
        api { noContent() }.deleteUser("u 1")
        assertEquals(HttpMethod.Delete, seen.single().method)
        assertEquals("/api/v1/admin/users/u%201", seen.single().path)
    }

    @Test
    fun resetPassword() = runTest {
        login()
        val grant = api { ok("""{"token":"reset-tok","expiresAt":"2026-11-11T08:00:00Z"}""") }.resetPassword("u1")
        assertEquals(HttpMethod.Post, seen.single().method)
        assertEquals("/api/v1/admin/users/u1/reset", seen.single().path)
        assertEquals("reset-tok", grant.token)
        assertFalse("reset-tok" in grant.toString())
    }

    @Test
    fun promote() = runTest {
        login()
        api { noContent() }.promote("u2", Role.VIEWER, "K7-4QX")
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/admin/users/u2/promote", request.path)
        val sent = body(request).jsonObject
        assertEquals("viewer", sent["role"]!!.jsonPrimitive.content)
        assertEquals("K7-4QX", sent["shortCode"]!!.jsonPrimitive.content)
    }

    @Test
    fun cancelPromotion() = runTest {
        login()
        api { noContent() }.cancelPromotion("u2")
        assertEquals(HttpMethod.Delete, seen.single().method)
        assertEquals("/api/v1/admin/users/u2/promote", seen.single().path)
    }

    @Test
    fun devicesAndRevoke() = runTest {
        login()
        val api = api { request -> if (request.method == HttpMethod.Get) ok("""[{"id":"d1","userId":"u1","userDisplayName":"Ana","deviceName":"Pixel","createdAt":"2026-11-01T08:00:00Z","revokedAt":"2026-11-10T08:00:00Z","revokeReason":"admin"}]""") else noContent() }
        val device = api.devices().single()
        assertTrue(device.revoked)
        assertEquals("Ana", device.userDisplayName)
        assertEquals("admin", device.revokeReason)
        api.revokeDevice("d1")
        assertEquals("/api/v1/admin/devices", seen[0].path)
        assertEquals(HttpMethod.Delete, seen[1].method)
        assertEquals("/api/v1/admin/devices/d1", seen[1].path)
    }

    @Test
    fun createInvite() = runTest {
        login()
        val invite = api { ok("""{"id":"i1","role":"member","token":"inv-tok","expiresAt":"2026-11-18T08:00:00Z"}""") }.createInvite(Role.EDITOR)
        assertEquals(HttpMethod.Post, seen.single().method)
        assertEquals("/api/v1/admin/invites", seen.single().path)
        assertEquals("""{"role":"member"}""", seen.single().body)
        assertEquals("inv-tok", invite.grant.token)
    }

    @Test
    fun invitesAndCancel() = runTest {
        login()
        val api = api { request -> if (request.method == HttpMethod.Get) ok("""[{"id":"i1","role":"viewer"}]""") else noContent() }
        assertEquals("i1", api.invites().single().id)
        api.cancelInvite("i1")
        assertEquals("/api/v1/admin/invites", seen[0].path)
        assertEquals(HttpMethod.Delete, seen[1].method)
        assertEquals("/api/v1/admin/invites/i1", seen[1].path)
    }

    @Test
    fun publicInvite() = runTest {
        login()
        val state = api { ok("""{"enabled":true,"guestLimit":20,"guestCount":3}""") }.publicInvite()
        assertEquals("/api/v1/admin/public-invite", seen.single().path)
        assertEquals(20, state.guestLimit)
        assertNull(state.token)
    }

    @Test
    fun updatePublicInviteSendsAnExplicitNullLimit() = runTest {
        login()
        api { ok("""{"enabled":false}""") }.updatePublicInvite(enabled = false, guestLimit = null)
        val request = seen.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/admin/public-invite", request.path)
        val sent = body(request).jsonObject
        assertEquals("false", sent["enabled"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, sent["guestLimit"])
    }

    @Test
    fun regeneratePublicInvite() = runTest {
        login()
        val state = api { ok("""{"enabled":true,"token":"pub-tok"}""") }.regeneratePublicInvite()
        assertEquals(HttpMethod.Post, seen.single().method)
        assertEquals("/api/v1/admin/public-invite/regenerate", seen.single().path)
        assertEquals("pub-tok", state.grant.token)
    }

    @Test
    fun downloadBackupStreams() = runTest {
        login()
        var received = ""
        api { respond(ByteReadChannel("BACKUP-BYTES"), HttpStatusCode.OK) }
            .downloadBackup { received = it.readRemaining().readByteArray().decodeToString() }
        assertEquals(HttpMethod.Get, seen.single().method)
        assertEquals("/api/v1/admin/backup", seen.single().path)
        assertEquals("BACKUP-BYTES", received)
    }

    @Test
    fun importBackupSendsTheFileWithTheMode() = runTest {
        login()
        api { noContent() }.importBackup(ServerImportMode.REPLACE, 5) { ByteReadChannel("hello") }
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/admin/backup", request.path)
        assertEquals("mode=replace", request.query)
        assertEquals("hello", request.body)
        assertEquals("application/octet-stream", request.headers[HttpHeaders.ContentType])
    }

    @Test
    fun settings() = runTest {
        login()
        val settings = api { ok("""{"serverName":"Atelier"}""") }.settings()
        assertEquals("/api/v1/admin/settings", seen.single().path)
        assertEquals("Atelier", settings.serverName)
    }

    @Test
    fun updateSettingsSendsTheServerNameAndNothingElse() = runTest {
        login()
        val api = api { ok("""{"serverName":"Atelier"}""") }
        api.updateSettings(AdminSettings(serverName = "Atelier"))
        val sent = body(seen[0]).jsonObject
        assertEquals(HttpMethod.Put, seen[0].method)
        assertEquals(setOf("serverName"), sent.keys)
        assertEquals("Atelier", sent["serverName"]!!.jsonPrimitive.content)
    }
}

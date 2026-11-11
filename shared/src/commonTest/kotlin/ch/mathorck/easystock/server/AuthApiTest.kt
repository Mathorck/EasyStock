package ch.mathorck.easystock.server

import ch.mathorck.easystock.auth.InMemorySessionStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthApiTest {
    private val base = "https://stock.example.org"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val problemHeaders = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private data class Seen(val method: HttpMethod, val path: String, val headers: Map<String, String?>, val body: String)

    private val seen = mutableListOf<Seen>()

    private suspend fun record(request: HttpRequestData) = Seen(
        request.method,
        request.url.encodedPath,
        listOf(HttpHeaders.Authorization, ClientVersions.APP_HEADER, ClientVersions.SCHEMA_HEADER).associateWith { request.headers[it] },
        request.body.toByteArray().decodeToString(),
    ).also { seen += it }

    private fun join(handler: suspend MockRequestHandleScope.(Seen) -> HttpResponseData): JoinApi =
        KtorJoinApi(createPublicClient(ServerConnection(base), MockEngine { handler(record(it)) }, ClientVersions("1.2.3", 9)))

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)

    private fun MockRequestHandleScope.problem(status: HttpStatusCode, type: String) =
        respond("""{"type":"$type","title":"x","status":${status.value}}""", status, problemHeaders)

    private val tokens = """{"accessToken":"acc","accessTokenExpiresAt":"2026-11-11T12:30:00Z","refreshToken":"ref",
        "user":{"id":"u1","displayName":"Jean","role":"member","kind":"full","shortCode":"K7Q-4QX"}}"""

    private fun body(s: Seen): JsonObject = Json.parseToJsonElement(s.body).jsonObject

    @Test
    fun serverInfoIsReadWithoutAToken() = runTest {
        val info = join {
            ok("""{"serverName":"Depot","serverVersion":"1.0.0","apiVersion":1,"minAppVersion":"0.0.0",
                "supportedSchemaVersions":[9],"setupDone":true,"hasData":false,"guestSignupEnabled":true,"future":1}""")
        }.serverInfo()
        assertEquals(ServerInfo("Depot", "1.0.0", 1, "0.0.0", listOf(9), setupDone = true, hasData = false, guestSignupEnabled = true), info)
        val request = seen.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/server-info", request.path)
        assertNull(request.headers[HttpHeaders.Authorization])
        assertEquals("1.2.3", request.headers[ClientVersions.APP_HEADER])
        assertEquals("9", request.headers[ClientVersions.SCHEMA_HEADER])
    }

    @Test
    fun serverInfoCompatibility() {
        val ok = ServerInfo(apiVersion = 1, minAppVersion = "1.2.0")
        assertNull(checkServerInfo(ok, "1.2.0"))
        assertNull(checkServerInfo(ok, "1.10.0-beta"))
        assertNull(checkServerInfo(ok.copy(minAppVersion = ""), "0.1"))
        assertEquals(ConnectError.APP_TOO_OLD, checkServerInfo(ok, "1.1.9"))
        assertEquals(ConnectError.API_UNSUPPORTED, checkServerInfo(ok.copy(apiVersion = 2), "9.9.9"))
        assertEquals(ConnectError.API_UNSUPPORTED, checkServerInfo(ServerInfo(), "1.0.0"))
    }

    @Test
    fun compareVersionsByNumber() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(compareVersions("1.2.3+45", "1.2.4") < 0)
    }

    @Test
    fun loginSendsTheCredentialsAndReadsTheTokens() = runTest {
        val answer = join { ok(tokens) }.login(LoginRequest("jean", "correct horse", "Pixel 10"))
        assertEquals("acc", answer.accessToken)
        assertEquals("ref", answer.refreshToken)
        assertEquals(SessionUser("u1", "Jean", "member", "full", "K7Q-4QX"), answer.user)
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/auth/login", request.path)
        assertNull(request.headers[HttpHeaders.Authorization])
        val sent = body(request)
        assertEquals("jean", sent["username"]!!.jsonPrimitive.content)
        assertEquals("correct horse", sent["password"]!!.jsonPrimitive.content)
        assertEquals("Pixel 10", sent["deviceName"]!!.jsonPrimitive.content)
    }

    @Test
    fun eachJoinCallHasItsEndpointAndBody() = runTest {
        val api = join { ok(tokens) }
        api.setup(SetupRequest("st", "admin", "Boss", "0123456789", "Pixel"))
        api.redeemInvite(RedeemInviteRequest("it", "marie", "Marie", "0123456789", "Pixel"))
        api.guestSignup(GuestSignupRequest("pt", "Visitor", "Pixel"))
        api.resetPassword(ResetPasswordRequest("rt", "0123456789", "Pixel"))
        assertEquals(
            listOf("/api/v1/auth/setup", "/api/v1/auth/invite/redeem", "/api/v1/auth/guest-signup", "/api/v1/auth/reset"),
            seen.map { it.path },
        )
        assertEquals(setOf("setupToken", "username", "displayName", "password", "deviceName"), body(seen[0]).keys)
        assertEquals("st", body(seen[0])["setupToken"]!!.jsonPrimitive.content)
        assertEquals(setOf("inviteToken", "username", "displayName", "password", "deviceName"), body(seen[1]).keys)
        assertEquals(setOf("publicToken", "displayName", "deviceName"), body(seen[2]).keys)
        assertEquals(setOf("resetToken", "password", "deviceName"), body(seen[3]).keys)
        assertTrue(seen.all { it.method == HttpMethod.Post && it.headers[HttpHeaders.Authorization] == null })
    }

    @Test
    fun aWrongPasswordIsNotARevokedSession() = runTest {
        val e = assertFailsWith<ApiException> {
            join { problem(HttpStatusCode.Unauthorized, "invalid-credentials") }.login(LoginRequest("jean", "wrong-password", "P"))
        }
        assertEquals(ConnectError.WRONG_CREDENTIALS, connectErrorOf(e))
        // One call: no refresh attempt
        assertEquals(1, seen.size)
    }

    @Test
    fun aWrongPasswordWithoutTypeIsStillAWrongPassword() = runTest {
        val e = assertFailsWith<ApiException> {
            join { respond("", HttpStatusCode.Unauthorized) }.login(LoginRequest("jean", "wrong-password", "P"))
        }
        assertEquals(ConnectError.WRONG_CREDENTIALS, connectErrorOf(e))
    }

    @Test
    fun problemTypesGiveTheirMessage() = runTest {
        val cases = listOf(
            HttpStatusCode.Unauthorized to "account-locked" to ConnectError.ACCOUNT_LOCKED,
            HttpStatusCode.Unauthorized to "account-disabled" to ConnectError.ACCOUNT_DISABLED,
            HttpStatusCode.NotFound to "invite-invalid" to ConnectError.CODE_INVALID,
            HttpStatusCode.Gone to "invite-expired" to ConnectError.CODE_EXPIRED,
            HttpStatusCode.Gone to "reset-expired" to ConnectError.CODE_EXPIRED,
            HttpStatusCode.Gone to "invite-used" to ConnectError.CODE_USED,
            HttpStatusCode.Gone to "reset-used" to ConnectError.CODE_USED,
            HttpStatusCode.Conflict to "setup-done" to ConnectError.SETUP_DONE,
            HttpStatusCode.Conflict to "username-taken" to ConnectError.USERNAME_TAKEN,
            HttpStatusCode.Forbidden to "guest-signup-off" to ConnectError.GUEST_SIGNUP_OFF,
            HttpStatusCode.Conflict to "guest-limit-reached" to ConnectError.GUEST_LIMIT_REACHED,
            HttpStatusCode.BadRequest to "validation-failed" to ConnectError.INVALID,
            HttpStatusCode.TooManyRequests to "rate-limited" to ConnectError.RATE_LIMITED,
            HttpStatusCode.UpgradeRequired to "app-too-old" to ConnectError.APP_TOO_OLD,
            HttpStatusCode.InternalServerError to "internal-error" to ConnectError.SERVER,
        )
        for ((pair, expected) in cases) {
            val (status, type) = pair
            val e = assertFailsWith<ApiException> {
                join { problem(status, type) }.redeemInvite(RedeemInviteRequest("t", "marie", "Marie", "0123456789", "P"))
            }
            assertEquals(expected, connectErrorOf(e), type)
        }
    }

    @Test
    fun a426WithoutBodyIsAppTooOld() = runTest {
        val e = assertFailsWith<ApiException> { join { respond("", HttpStatusCode.UpgradeRequired) }.login(LoginRequest("a", "b", "c")) }
        assertEquals(ConnectError.APP_TOO_OLD, connectErrorOf(e))
    }

    @Test
    fun noAnswerIsANetworkError() = runTest {
        val e = assertFailsWith<ApiException> { join { throw IOException("unreachable") }.serverInfo() }
        assertEquals(ApiError.NETWORK, e.error)
        assertEquals(ConnectError.NETWORK, connectErrorOf(e))
    }

    @Test
    fun anAnswerThatIsNotEasyStockIsAServerError() = runTest {
        val e = assertFailsWith<ApiException> { join { respond("<html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }.serverInfo() }
        assertEquals(ConnectError.SERVER, connectErrorOf(e))
    }

    // --- Account calls, with the session ---

    private val session = InMemorySessionStore()
    private var revokedCalls = 0

    private fun account(handler: suspend MockRequestHandleScope.(Seen) -> HttpResponseData): AccountApi = KtorAccountApi(
        createServerClient(ServerConnection(base), MockEngine { handler(record(it)) }, session, ClientVersions("1.2.3", 9)) { revokedCalls++ },
    )

    @Test
    fun meReadsThePendingPromotion() = runTest {
        session.accessToken = "acc"
        val me = account {
            ok("""{"id":"g1","displayName":"Visitor","role":"guest","kind":"guest","shortCode":"K7Q-4QX",
                "pendingPromotion":{"role":"member","expiresAt":"2026-11-18T10:00:00Z"}}""")
        }.me()
        assertEquals("K7Q-4QX", me.shortCode)
        assertEquals(PendingPromotion("member", "2026-11-18T10:00:00Z"), me.pendingPromotion)
        assertEquals("/api/v1/me", seen.single().path)
        assertEquals("Bearer acc", seen.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun meWithoutPromotion() = runTest {
        session.accessToken = "acc"
        assertNull(account { ok("""{"id":"u1","displayName":"Jean","role":"admin","kind":"full"}""") }.me().pendingPromotion)
    }

    @Test
    fun logoutAndCompletePromotion() = runTest {
        session.accessToken = "acc"
        val api = account { if (it.path.endsWith("logout")) respond("", HttpStatusCode.NoContent) else ok(tokens) }
        val answer = api.completePromotion(CompletePromotionRequest("visitor", "0123456789"))
        api.logout()
        assertEquals("ref", answer.refreshToken)
        assertEquals(listOf("/api/v1/auth/complete-promotion", "/api/v1/auth/logout"), seen.map { it.path })
        assertEquals(setOf("username", "password"), body(seen[0]).keys)
        assertTrue(seen.all { it.method == HttpMethod.Post && it.headers[HttpHeaders.Authorization] == "Bearer acc" })
        assertEquals(0, revokedCalls)
    }

    @Test
    fun expiredPromotion() = runTest {
        session.accessToken = "acc"
        val e = assertFailsWith<ApiException> {
            account { problem(HttpStatusCode.Gone, "promotion-expired") }.completePromotion(CompletePromotionRequest("v", "0123456789"))
        }
        assertEquals(ConnectError.CODE_EXPIRED, connectErrorOf(e))
        assertFalse(revokedCalls > 0)
    }

    @Test
    fun accountRules() {
        assertTrue(AccountRules.isValidUsername("jean.d-1_x"))
        assertFalse(AccountRules.isValidUsername("je"))
        assertFalse(AccountRules.isValidUsername("jean dupont"))
        assertFalse(AccountRules.isValidUsername("a".repeat(33)))
        assertTrue(AccountRules.isValidPassword("0123456789"))
        assertFalse(AccountRules.isValidPassword("012345678"))
        assertFalse(AccountRules.isValidPassword("x".repeat(129)))
        assertTrue(AccountRules.isValidDisplayName(" Jean "))
        assertFalse(AccountRules.isValidDisplayName("   "))
        assertFalse(AccountRules.isValidDisplayName("x".repeat(51)))
    }
}

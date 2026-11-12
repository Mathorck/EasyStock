package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.data.db.DB_VERSION
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ClientVersions
import ch.mathorck.easystock.server.KtorAccountApi
import ch.mathorck.easystock.server.checkServerInfo
import ch.mathorck.easystock.server.createServerClient
import ch.mathorck.easystock.server.serverHttpEngine
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Scenarios 1 and 2: server-info, version headers, setup, login, token refresh, logout. */
class AuthE2eTest {
    @BeforeTest
    fun server() = E2eServer.assume()

    @Test
    fun serverInfoAndVersionHeadersAreAccepted() = runBlocking {
        val fixture = E2eServer.fixture
        val info = E2eServer.joinApi().use { it.serverInfo() }
        assertNull(checkServerInfo(info, E2eServer.versions.appVersion))
        assertTrue(DB_VERSION in info.supportedSchemaVersions, "schema $DB_VERSION in ${info.supportedSchemaVersions}")
        assertTrue(info.setupDone)
        // A call with the headers of this app
        assertEquals("admin", fixture.admin.account { it.me() }.role)
        // An unknown schema version is refused with 426
        val old = createServerClient(E2eServer.connection, serverHttpEngine(null), fixture.admin.store, ClientVersions("1.0.0", schemaVersion = 1))
        val error = old.use { client -> assertFailsWith<ApiException> { KtorAccountApi(client).me() } }
        assertEquals(ApiError.APP_TOO_OLD, error.error)
    }

    @Test
    fun loginRefreshAndLogout() = runBlocking {
        val fixture = E2eServer.fixture
        val session = fixture.login(fixture.admin, "e2e logout")
        assertEquals(fixture.admin.user.id, session.user.id)
        val firstRefresh = session.store.refreshToken()
        // An access token the server refuses: the client refreshes it once and sends the call again
        session.store.accessToken = "expired"
        assertEquals("admin", session.account { it.me() }.role)
        assertNotEquals(firstRefresh, session.store.refreshToken(), "refresh token rotated")
        assertNotEquals("expired", session.store.accessToken)

        session.client().use { client ->
            KtorAccountApi(client).logout()
            val error = assertFailsWith<ApiException> { KtorAccountApi(client).me() }
            assertEquals(ApiError.SESSION_REVOKED, error.error)
        }
        assertNull(session.store.refreshToken(), "tokens forgotten")
        // The other sessions of the account still work
        assertEquals("admin", fixture.admin.account { it.me() }.role)
    }
}

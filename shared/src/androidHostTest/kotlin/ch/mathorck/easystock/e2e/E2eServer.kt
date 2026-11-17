package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.server.ClientVersions
import ch.mathorck.easystock.services.server.KtorJoinApi
import ch.mathorck.easystock.services.server.ServerConnection
import ch.mathorck.easystock.services.server.createPublicClient
import ch.mathorck.easystock.services.server.serverHttpEngine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume

/**
 * The live server of the end-to-end tests: `-Peasystock.e2e.url` and `-Peasystock.e2e.setupToken` (Gradle),
 * or EASYSTOCK_E2E_URL and EASYSTOCK_E2E_SETUP_TOKEN. Without a URL every E2E test is skipped.
 * Started by `server/e2e/run.sh` of the server repository.
 */
object E2eServer {
    private val url: String? = setting("easystock.e2e.url", "EASYSTOCK_E2E_URL")
    private val setupToken: String? = setting("easystock.e2e.setupToken", "EASYSTOCK_E2E_SETUP_TOKEN")

    val versions = ClientVersions(appVersion = "1.0.0")

    val connection: ServerConnection get() = ServerConnection(url!!.trimEnd('/'))

    /** Skips the calling test when no server is given. */
    fun assume() {
        Assume.assumeTrue("No E2E server (-Peasystock.e2e.url)", !url.isNullOrBlank())
    }

    private fun setting(property: String, env: String): String? =
        System.getProperty(property)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    fun joinApi(): KtorJoinApi = KtorJoinApi(createPublicClient(connection, serverHttpEngine(null), versions))

    /**
     * The auth calls (setup, login, invites) share 10 requests a minute per IP: a 429 waits for the next
     * window instead of failing the test.
     */
    suspend fun <T> authCall(block: suspend () -> T): T {
        repeat(20) {
            try {
                return block()
            } catch (e: ApiException) {
                if (e.error != ApiError.RATE_LIMITED) throw e
                delay(5_000)
            }
        }
        return block()
    }

    /** The first Admin and three accounts, made once for the whole run (setup works only once). */
    val fixture: Fixture by lazy { runBlocking { Fixture.create() } }

    @OptIn(ExperimentalUuidApi::class)
    fun unique(prefix: String): String = "$prefix-${Uuid.random().toString().take(8)}"
}

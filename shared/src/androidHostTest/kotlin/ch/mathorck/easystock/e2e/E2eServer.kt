package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.auth.SessionStore
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ClientVersions
import ch.mathorck.easystock.server.JoinCode
import ch.mathorck.easystock.server.JoinKind
import ch.mathorck.easystock.server.KtorAccountApi
import ch.mathorck.easystock.server.KtorAdminApi
import ch.mathorck.easystock.server.KtorJoinApi
import ch.mathorck.easystock.server.LoginRequest
import ch.mathorck.easystock.server.RedeemInviteRequest
import ch.mathorck.easystock.server.ServerClient
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.SessionUser
import ch.mathorck.easystock.server.SetupRequest
import ch.mathorck.easystock.server.TokenAnswer
import ch.mathorck.easystock.server.createPublicClient
import ch.mathorck.easystock.server.createServerClient
import ch.mathorck.easystock.server.serverHttpEngine
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

/** The tokens of one device session: the refresh token rotates, the clients of the session share it. */
class InMemorySessionStore : SessionStore {
    @Volatile override var accessToken: String? = null

    @Volatile private var refresh: String? = null

    override suspend fun refreshToken(): String? = refresh

    override suspend fun saveRefreshToken(token: String) {
        refresh = token
    }

    override suspend fun clear() {
        accessToken = null
        refresh = null
    }
}

/** One device session of an account on the server. */
class Session(val user: SessionUser, val username: String, var password: String, answer: TokenAnswer) {
    val store = InMemorySessionStore().apply {
        accessToken = answer.accessToken
        runBlocking { saveRefreshToken(answer.refreshToken) }
    }

    /** The answer [ch.mathorck.easystock.auth.ServerJoin] commits on a new phone, with the current tokens. */
    suspend fun tokenAnswer(): TokenAnswer =
        TokenAnswer(store.accessToken ?: "", checkNotNull(store.refreshToken()) { "Session ${user.displayName} ended" }, user = user)

    /** A client of this session, as the account and admin screens use it. */
    fun client(): ServerClient = createServerClient(E2eServer.connection, serverHttpEngine(null), store, E2eServer.versions)

    fun <T> admin(block: suspend (KtorAdminApi) -> T): T = client().use { runBlocking { block(KtorAdminApi(it)) } }

    fun <T> account(block: suspend (KtorAccountApi) -> T): T = client().use { runBlocking { block(KtorAccountApi(it)) } }
}

class Fixture private constructor(val admin: Session, val alice: Session, val bob: Session, val viewer: Session) {
    /** Admin makes an invite, the app reads its QR code and redeems it: a new account and its first session. */
    suspend fun invite(role: Role, name: String = E2eServer.unique(role.dbValue)): Session {
        val invite = admin.client().use { KtorAdminApi(it).createInvite(role) }
        val qr = checkNotNull(invite.grant.qrText(E2eServer.connection, JoinKind.INVITE)) { "Invite without QR code" }
        val code = JoinCode.parse(qr)
        check(code.kind == JoinKind.INVITE)
        val password = "pw-$name-0123456789"
        val answer = E2eServer.joinApi().use { api ->
            E2eServer.authCall { api.redeemInvite(RedeemInviteRequest(code.token, name, "Name $name", password, "e2e $name")) }
        }
        return Session(checkNotNull(answer.user), name, password, answer)
    }

    /** A new device session of an existing account. */
    suspend fun login(of: Session, device: String = "e2e login"): Session {
        val answer = E2eServer.joinApi().use { api ->
            E2eServer.authCall { api.login(LoginRequest(of.username, of.password, device)) }
        }
        return Session(checkNotNull(answer.user), of.username, of.password, answer)
    }

    companion object {
        private const val ADMIN_PASSWORD = "admin-password-0123456789"

        /**
         * Sets the server up, or logs in as its Admin when an earlier run did (to run a few tests again on the
         * same server). The other accounts are new for every run.
         */
        suspend fun create(): Fixture {
            val info = E2eServer.joinApi().use { it.serverInfo() }
            val answer = E2eServer.joinApi().use { api ->
                if (info.setupDone) {
                    E2eServer.authCall { api.login(LoginRequest("admin", ADMIN_PASSWORD, "e2e admin")) }
                } else {
                    val token = checkNotNull(
                        System.getProperty("easystock.e2e.setupToken") ?: System.getenv("EASYSTOCK_E2E_SETUP_TOKEN"),
                    ) { "No setup token (-Peasystock.e2e.setupToken)" }
                    E2eServer.authCall { api.setup(SetupRequest(token, "admin", "Admin", ADMIN_PASSWORD, "e2e admin")) }
                }
            }
            val admin = Session(checkNotNull(answer.user), "admin", ADMIN_PASSWORD, answer)
            val partial = Fixture(admin, admin, admin, admin)
            return Fixture(
                admin,
                partial.invite(Role.EDITOR, E2eServer.unique("alice")),
                partial.invite(Role.EDITOR, E2eServer.unique("bob")),
                partial.invite(Role.VIEWER, E2eServer.unique("victor")),
            )
        }
    }
}

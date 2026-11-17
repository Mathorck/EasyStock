package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.data.dto.server.SessionUser
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.services.auth.InMemorySessionStore
import ch.mathorck.easystock.services.server.KtorAccountApi
import ch.mathorck.easystock.services.server.KtorAdminApi
import ch.mathorck.easystock.services.server.ServerClient
import ch.mathorck.easystock.services.server.createServerClient
import ch.mathorck.easystock.services.server.serverHttpEngine
import kotlinx.coroutines.runBlocking

/** One device session of an account on the server. */
class Session(val user: SessionUser, val username: String, var password: String, answer: TokenAnswer) {
    val store = InMemorySessionStore().apply {
        accessToken = answer.accessToken
        runBlocking { saveRefreshToken(answer.refreshToken) }
    }

    /** The answer [ch.mathorck.easystock.services.auth.ServerJoin] commits on a new phone, with the current tokens. */
    suspend fun tokenAnswer(): TokenAnswer =
        TokenAnswer(store.accessToken ?: "", checkNotNull(store.refreshToken()) { "Session ${user.displayName} ended" }, user = user)

    /** A client of this session, as the account and admin screens use it. */
    fun client(): ServerClient = createServerClient(E2eServer.connection, serverHttpEngine(null), store, E2eServer.versions)

    fun <T> admin(block: suspend (KtorAdminApi) -> T): T = client().use { runBlocking { block(KtorAdminApi(it)) } }

    fun <T> account(block: suspend (KtorAccountApi) -> T): T = client().use { runBlocking { block(KtorAccountApi(it)) } }
}

package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.GuestSignupRequest
import ch.mathorck.easystock.data.dto.server.LoginRequest
import ch.mathorck.easystock.data.dto.server.RedeemInviteRequest
import ch.mathorck.easystock.data.dto.server.ResetPasswordRequest
import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.server.SetupRequest
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post

/** [JoinApi] over a [createPublicClient] client. */
class KtorJoinApi(private val http: HttpClient) : JoinApi, AutoCloseable {
    override suspend fun serverInfo(): ServerInfo = apiCall { http.get("server-info").body() }

    override suspend fun login(request: LoginRequest): TokenAnswer = post("auth/login", request)

    override suspend fun setup(request: SetupRequest): TokenAnswer = post("auth/setup", request)

    override suspend fun redeemInvite(request: RedeemInviteRequest): TokenAnswer = post("auth/invite/redeem", request)

    override suspend fun guestSignup(request: GuestSignupRequest): TokenAnswer = post("auth/guest-signup", request)

    override suspend fun resetPassword(request: ResetPasswordRequest): TokenAnswer = post("auth/reset", request)

    private suspend inline fun <reified T> post(path: String, body: T): TokenAnswer = apiCall {
        http.post(path) { json(body) }.body()
    }

    override fun close() = http.close()
}

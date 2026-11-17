package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.GuestSignupRequest
import ch.mathorck.easystock.data.dto.server.LoginRequest
import ch.mathorck.easystock.data.dto.server.RedeemInviteRequest
import ch.mathorck.easystock.data.dto.server.ResetPasswordRequest
import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.server.SetupRequest
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.viewmodels.server.ANSWER

internal class FakeJoinApi(val connection: ServerConnection) : JoinApi, AutoCloseable {
    var info: ServerInfo = ServerInfo(apiVersion = 1, setupDone = true, guestSignupEnabled = true)
    var infoError: ApiException? = null
    var authError: ApiException? = null
    val calls = mutableListOf<Any>()
    var closed = false

    override suspend fun serverInfo(): ServerInfo {
        calls += "server-info"
        infoError?.let { throw it }
        return info
    }

    private fun answer(request: Any): TokenAnswer {
        calls += request
        authError?.let { throw it }
        return ANSWER
    }

    override suspend fun login(request: LoginRequest) = answer(request)

    override suspend fun setup(request: SetupRequest) = answer(request)

    override suspend fun redeemInvite(request: RedeemInviteRequest) = answer(request)

    override suspend fun guestSignup(request: GuestSignupRequest) = answer(request)

    override suspend fun resetPassword(request: ResetPasswordRequest) = answer(request)

    override fun close() {
        closed = true
    }
}

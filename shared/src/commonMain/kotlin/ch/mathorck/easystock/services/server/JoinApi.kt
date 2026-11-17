package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.GuestSignupRequest
import ch.mathorck.easystock.data.dto.server.LoginRequest
import ch.mathorck.easystock.data.dto.server.RedeemInviteRequest
import ch.mathorck.easystock.data.dto.server.ResetPasswordRequest
import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.server.SetupRequest
import ch.mathorck.easystock.data.dto.server.TokenAnswer

/**
 * The calls made before joining a server, without a token. Every call throws [ApiException] on failure;
 * [connectErrorOf] tells the user what went wrong.
 */
interface JoinApi {
    suspend fun serverInfo(): ServerInfo

    suspend fun login(request: LoginRequest): TokenAnswer

    suspend fun setup(request: SetupRequest): TokenAnswer

    suspend fun redeemInvite(request: RedeemInviteRequest): TokenAnswer

    suspend fun guestSignup(request: GuestSignupRequest): TokenAnswer

    suspend fun resetPassword(request: ResetPasswordRequest): TokenAnswer
}

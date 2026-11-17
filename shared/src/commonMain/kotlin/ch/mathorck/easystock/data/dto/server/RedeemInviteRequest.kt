package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /auth/invite/redeem`. */
@Serializable
data class RedeemInviteRequest(
    val inviteToken: String,
    val username: String,
    val displayName: String,
    val password: String,
    val deviceName: String,
) {
    override fun toString(): String = "RedeemInviteRequest(username=$username)"
}

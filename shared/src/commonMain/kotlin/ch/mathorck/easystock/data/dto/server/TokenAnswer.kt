package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** Answer of setup, login, refresh, invite redeem, reset and promotion. */
@Serializable
data class TokenAnswer(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String? = null,
    val user: SessionUser? = null,
) {
    // Tokens must not end up in logs
    override fun toString(): String = "TokenAnswer(user=${user?.id})"
}

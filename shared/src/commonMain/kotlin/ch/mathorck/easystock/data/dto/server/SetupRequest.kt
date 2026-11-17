package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /auth/setup`: the first Admin. */
@Serializable
data class SetupRequest(
    val setupToken: String,
    val username: String,
    val displayName: String,
    val password: String,
    val deviceName: String,
) {
    override fun toString(): String = "SetupRequest(username=$username)"
}

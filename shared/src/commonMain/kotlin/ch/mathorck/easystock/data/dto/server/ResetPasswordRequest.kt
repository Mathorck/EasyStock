package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /auth/reset`: new password from a `reset` code. Revokes the other sessions of the user. */
@Serializable
data class ResetPasswordRequest(val resetToken: String, val password: String, val deviceName: String) {
    override fun toString(): String = "ResetPasswordRequest"
}

package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /me/password`: answered 204. Wrong [currentPassword] is 400 `invalid-credentials`. */
@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String) {
    override fun toString(): String = "ChangePasswordRequest"
}

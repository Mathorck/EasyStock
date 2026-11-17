package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

// Bodies of the /auth and /me endpoints (03-server-spec.md, "Auth" and "Me").
// PROVISIONAL: the server only implements /server-info yet. Field names are the camelCase forms of the
// spec prose ("takes the setup token, username, display name, password and device name"), the token
// field named after its kind like the existing `refreshToken`. Passwords and tokens never in toString().

/** `POST /auth/login`. */
@Serializable
data class LoginRequest(val username: String, val password: String, val deviceName: String) {
    override fun toString(): String = "LoginRequest(username=$username)"
}

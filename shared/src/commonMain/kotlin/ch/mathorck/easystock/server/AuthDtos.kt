package ch.mathorck.easystock.server

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

/** `POST /auth/guest-signup`: the printed code. */
@Serializable
data class GuestSignupRequest(val publicToken: String, val displayName: String, val deviceName: String) {
    override fun toString(): String = "GuestSignupRequest"
}

/** `POST /auth/reset`: new password from a `reset` code. Revokes the other sessions of the user. */
@Serializable
data class ResetPasswordRequest(val resetToken: String, val password: String, val deviceName: String) {
    override fun toString(): String = "ResetPasswordRequest"
}

/** `POST /auth/complete-promotion` (with the Guest's bearer token). */
@Serializable
data class CompletePromotionRequest(val username: String, val password: String) {
    override fun toString(): String = "CompletePromotionRequest(username=$username)"
}

/** `GET /me`: the user, role, kind, short code and the pending promotion if there is one. */
@Serializable
data class MeAnswer(
    val id: String = "",
    val displayName: String = "",
    val role: String = "",
    val kind: String = "",
    /** Blank for a full account. */
    val shortCode: String = "",
    val alertsEnabled: Boolean? = null,
    /** Same shape as in the admin user list ([PendingPromotion]). */
    val pendingPromotion: PendingPromotion? = null,
)

/** `PATCH /me` with the alert preference (the server also takes `displayName`, which the app does not send here). */
@Serializable
data class UpdateAlertsRequest(val alertsEnabled: Boolean)

/** `PATCH /me` with the display name only. */
@Serializable
data class UpdateDisplayNameRequest(val displayName: String)

/** `POST /me/password`: answered 204. Wrong [currentPassword] is 400 `invalid-credentials`. */
@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String) {
    override fun toString(): String = "ChangePasswordRequest"
}

/** Rules of the account fields (03-server-spec.md, "Accounts"), checked before sending. */
object AccountRules {
    const val PASSWORD_MIN = 10
    const val PASSWORD_MAX = 128
    const val DISPLAY_NAME_MAX = 50
    private val USERNAME = Regex("^[A-Za-z0-9._-]{3,32}$")

    fun isValidUsername(username: String): Boolean = USERNAME.matches(username)

    fun isValidPassword(password: String): Boolean = password.length in PASSWORD_MIN..PASSWORD_MAX

    fun isValidDisplayName(name: String): Boolean = name.trim().length in 1..DISPLAY_NAME_MAX
}

package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/**
 * `DELETE /me`: the user deletes the own account, answered 204. [password] is the current password of a full
 * account; a Guest has none and sends `{}` (null is not sent). A wrong password is 400 `invalid-credentials`
 * (like `POST /me/password`), a locked account 429 `rate-limited`, the last active Admin 409 `last-admin`.
 */
@Serializable
data class DeleteMeRequest(val password: String? = null) {
    override fun toString(): String = "DeleteMeRequest"
}

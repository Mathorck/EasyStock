package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /auth/complete-promotion` (with the Guest's bearer token). */
@Serializable
data class CompletePromotionRequest(val username: String, val password: String) {
    override fun toString(): String = "CompletePromotionRequest(username=$username)"
}

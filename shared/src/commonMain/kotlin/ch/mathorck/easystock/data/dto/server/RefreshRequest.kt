package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /auth/refresh`. */
@Serializable
data class RefreshRequest(val refreshToken: String) {
    override fun toString(): String = "RefreshRequest"
}

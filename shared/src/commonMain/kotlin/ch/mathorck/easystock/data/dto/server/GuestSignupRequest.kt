package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /auth/guest-signup`: the printed code. */
@Serializable
data class GuestSignupRequest(val publicToken: String, val displayName: String, val deviceName: String) {
    override fun toString(): String = "GuestSignupRequest"
}

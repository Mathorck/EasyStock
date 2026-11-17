package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** User part of a token answer. */
@Serializable
data class SessionUser(
    val id: String,
    val displayName: String = "",
    val role: String = "",
    val kind: String = "",
    val shortCode: String = "",
)

package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `GET`/`PUT /admin/settings`: exactly this one field (the server refuses unknown keys). */
@Serializable
data class AdminSettings(
    val serverName: String = "",
)

package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `PATCH /me` with the display name only. */
@Serializable
data class UpdateDisplayNameRequest(val displayName: String)

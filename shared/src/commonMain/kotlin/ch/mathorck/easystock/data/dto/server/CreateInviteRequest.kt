package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /admin/invites`. */
@Serializable
data class CreateInviteRequest(val role: String)

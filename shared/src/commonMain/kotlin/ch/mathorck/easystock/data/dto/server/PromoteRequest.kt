package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `POST /admin/users/{id}/promote`. [shortCode] is the code the Admin typed, as shown by the Guest. */
@Serializable
data class PromoteRequest(val role: String, val shortCode: String)

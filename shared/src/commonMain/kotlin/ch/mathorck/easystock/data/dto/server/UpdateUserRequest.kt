package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `PATCH /admin/users/{id}`: only the fields that are set are sent. */
@Serializable
data class UpdateUserRequest(
    val role: String? = null,
    val displayName: String? = null,
    val disabled: Boolean? = null,
)

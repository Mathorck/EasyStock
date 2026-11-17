package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** RFC 9457 Problem Details, as sent by the server for every error. */
@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
)

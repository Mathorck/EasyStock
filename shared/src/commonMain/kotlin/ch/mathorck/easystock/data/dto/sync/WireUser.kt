package ch.mathorck.easystock.data.dto.sync

import kotlinx.serialization.Serializable

@Serializable
data class WireUser(val id: String, val displayName: String)

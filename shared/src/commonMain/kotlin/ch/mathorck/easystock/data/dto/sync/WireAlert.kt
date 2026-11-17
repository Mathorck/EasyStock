package ch.mathorck.easystock.data.dto.sync

import kotlinx.serialization.Serializable

@Serializable
data class WireAlert(val id: String, val articleId: String, val crossedAt: Long, val stock: Long)

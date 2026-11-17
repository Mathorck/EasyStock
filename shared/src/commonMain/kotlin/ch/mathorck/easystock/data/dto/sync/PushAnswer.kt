package ch.mathorck.easystock.data.dto.sync

import kotlinx.serialization.Serializable

@Serializable
data class PushAnswer(val results: List<PushResult> = emptyList())

package ch.mathorck.easystock.enums.article

import ch.mathorck.easystock.data.model.ItemStatusSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ItemStatusSerializer::class)
enum class ItemStatus { AVAILABLE, LENT, REPAIR, LOST }

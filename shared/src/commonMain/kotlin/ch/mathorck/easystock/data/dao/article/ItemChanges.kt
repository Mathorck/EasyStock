package ch.mathorck.easystock.data.dao.article

import ch.mathorck.easystock.enums.article.ItemStatus
import kotlinx.serialization.json.JsonObject

/** New values of the editable properties of a unit. The location is not one of them (it comes from the movements). */
data class ItemChanges(
    val serialNumber: String?,
    val status: ItemStatus,
    val attributes: JsonObject?,
    val archived: Boolean,
)

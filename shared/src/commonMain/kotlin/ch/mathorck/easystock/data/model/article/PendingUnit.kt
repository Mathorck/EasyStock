package ch.mathorck.easystock.data.model.article

import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.dbValue
import ch.mathorck.easystock.enums.enumFromDb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A unit found during a review that is not registered yet. Stored as JSON on the review line until the review is applied. */
data class PendingUnit(
    val serialNumber: String? = null,
    val status: ItemStatus = ItemStatus.AVAILABLE,
    /** Unit-specific attribute values, same keys as [Article.attributes]. */
    val attributes: JsonObject? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("serial_number", serialNumber)
        put("status", status.dbValue)
        attributes?.let { put("attributes", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): PendingUnit = PendingUnit(
            serialNumber = (json["serial_number"] as? JsonPrimitive)?.contentOrNull,
            status = json["status"]?.jsonPrimitive?.contentOrNull?.let { enumFromDb<ItemStatus>(it) } ?: ItemStatus.AVAILABLE,
            attributes = (json["attributes"] as? JsonObject),
        )
    }
}

package ch.mathorck.easystock.data.db

import androidx.room.TypeConverter
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.data.model.ReviewStatus
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.data.model.dbValue
import ch.mathorck.easystock.data.model.enumFromDb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Converters {
    @TypeConverter fun fromAttributeDataType(value: AttributeDataType): String = value.dbValue
    @TypeConverter fun toAttributeDataType(value: String): AttributeDataType = enumFromDb(value)

    @TypeConverter fun fromTracking(value: Tracking): String = value.dbValue
    @TypeConverter fun toTracking(value: String): Tracking = enumFromDb(value)

    @TypeConverter fun fromItemStatus(value: ItemStatus): String = value.dbValue
    @TypeConverter fun toItemStatus(value: String): ItemStatus = enumFromDb(value)

    @TypeConverter fun fromMovementReason(value: MovementReason): String = value.dbValue
    @TypeConverter fun toMovementReason(value: String): MovementReason = enumFromDb(value)

    @TypeConverter fun fromReviewStatus(value: ReviewStatus): String = value.dbValue
    @TypeConverter fun toReviewStatus(value: String): ReviewStatus = enumFromDb(value)

    @TypeConverter fun fromReviewResult(value: ReviewResult): String = value.dbValue
    @TypeConverter fun toReviewResult(value: String): ReviewResult = enumFromDb(value)

    @TypeConverter fun fromJsonObject(value: JsonObject?): String? = value?.toString()
    @TypeConverter fun toJsonObject(value: String?): JsonObject? =
        value?.let { Json.parseToJsonElement(it).jsonObject }

    @TypeConverter fun fromStringList(value: List<String>?): String? =
        value?.let { list -> JsonArray(list.map { JsonPrimitive(it) }).toString() }
    @TypeConverter fun toStringList(value: String?): List<String>? =
        value?.let { Json.parseToJsonElement(it).jsonArray.map { e -> e.jsonPrimitive.content } }
}

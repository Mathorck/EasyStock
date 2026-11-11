package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Physical count of a location and all its active descendants.
 * Nothing changes the stock until it is finished: the corrections are then new [StockMovement] rows.
 */
@Entity(
    tableName = "stock_review",
    foreignKeys = [
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["location_id"]),
    ],
    indices = [
        Index(value = ["location_id"]),
        Index(value = ["updated_at"]),
    ],
)
@Serializable
data class StockReview(
    @PrimaryKey val id: String,
    /** Root of the scope. */
    @SerialName("location_id") @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(defaultValue = "open") val status: ReviewStatus = ReviewStatus.OPEN,
    @SerialName("started_at") @ColumnInfo(name = "started_at") val startedAt: Long,
    @SerialName("finished_at") @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** One position to check: an article at a location (quantity) or one serial unit. */
@Entity(
    tableName = "stock_review_line",
    foreignKeys = [
        ForeignKey(entity = StockReview::class, parentColumns = ["id"], childColumns = ["review_id"]),
        ForeignKey(entity = Article::class, parentColumns = ["id"], childColumns = ["article_id"]),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"]),
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["location_id"]),
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["found_location_id"]),
    ],
    indices = [
        Index(value = ["review_id"]),
        Index(value = ["article_id"]),
        Index(value = ["item_id"]),
        Index(value = ["location_id"]),
        Index(value = ["found_location_id"]),
        Index(value = ["updated_at"]),
    ],
)
@Serializable
data class StockReviewLine(
    @PrimaryKey val id: String,
    @SerialName("review_id") @ColumnInfo(name = "review_id") val reviewId: String,
    @SerialName("article_id") @ColumnInfo(name = "article_id") val articleId: String,
    @SerialName("item_id") @ColumnInfo(name = "item_id") val itemId: String? = null,
    @SerialName("location_id") @ColumnInfo(name = "location_id") val locationId: String,
    /** Stock at [locationId] when the line was last checked, thousandths. 0 for a position found unexpectedly. */
    @SerialName("expected_quantity") @ColumnInfo(name = "expected_quantity") val expectedMilli: Long,
    /** Counted quantity, thousandths. Null while not counted. */
    @SerialName("counted_quantity") @ColumnInfo(name = "counted_quantity") val countedMilli: Long? = null,
    @ColumnInfo(defaultValue = "unchecked") val result: ReviewResult = ReviewResult.UNCHECKED,
    @SerialName("found_location_id") @ColumnInfo(name = "found_location_id") val foundLocationId: String? = null,
    @SerialName("info_flagged") @ColumnInfo(name = "info_flagged", defaultValue = "0") val infoFlagged: Boolean = false,
    val note: String? = null,
    /**
     * When the flagged info was fixed (from the article page, once the review is finished). Null while the flag is open.
     * Setting it also sets [updatedAt], so a backup merge keeps the fix.
     */
    @SerialName("info_fixed_at") @ColumnInfo(name = "info_fixed_at") val infoFixedAt: Long? = null,
    /**
     * A new unit found during the review, not created yet (see [PendingUnit]). The line has no [itemId] until the
     * review is finished: the unit and its movement are then created and this is cleared.
     */
    @SerialName("pending_item") @ColumnInfo(name = "pending_item") val pendingItem: JsonObject? = null,
    @SerialName("checked_at") @ColumnInfo(name = "checked_at") val checkedAt: Long? = null,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

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

val StockReviewLine.pendingUnit: PendingUnit? get() = pendingItem?.let(PendingUnit::fromJson)

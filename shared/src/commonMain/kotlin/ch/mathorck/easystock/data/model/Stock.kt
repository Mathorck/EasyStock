package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonObject

@Entity(
    tableName = "location",
    foreignKeys = [
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["parent_id"]),
    ],
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class Location(
    @PrimaryKey val id: String,
    val name: String,
    /** Hierarchy: room > shelf > bin. */
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** Individual unit, only for articles with [Tracking.SERIAL]. */
@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(entity = Article::class, parentColumns = ["id"], childColumns = ["article_id"]),
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["location_id"]),
    ],
    indices = [
        Index(value = ["serial_number"], unique = true),
        Index(value = ["article_id"]),
        Index(value = ["location_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class Item(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "serial_number") val serialNumber: String? = null,
    @ColumnInfo(defaultValue = "available") val status: ItemStatus = ItemStatus.AVAILABLE,
    /** Current location. Cache only, the movements are the source of truth. */
    @ColumnInfo(name = "location_id") val locationId: String? = null,
    /** Unit-specific values, same keys as [Article.attributes]. */
    val attributes: JsonObject? = null,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/**
 * Append-only: never updated or deleted. Stock = SUM(quantity).
 * A transfer is two rows (- at the source, + at the destination).
 */
@Entity(
    tableName = "stock_movement",
    foreignKeys = [
        ForeignKey(entity = Article::class, parentColumns = ["id"], childColumns = ["article_id"]),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"]),
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["location_id"]),
    ],
    indices = [
        Index(value = ["article_id", "location_id"]),
        Index(value = ["item_id"]),
        Index(value = ["location_id"]),
        Index(value = ["created_at"]),
    ],
)
data class StockMovement(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "article_id") val articleId: String,
    /** Set when the article uses [Tracking.SERIAL]. */
    @ColumnInfo(name = "item_id") val itemId: String? = null,
    @ColumnInfo(name = "location_id") val locationId: String,
    /** + in / - out, in thousandths of the article unit (1.5 kg = 1500). */
    @ColumnInfo(name = "quantity") val quantityMilli: Long,
    val reason: MovementReason,
    val comment: String? = null,
    /** When it happened (may be offline). */
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    /** When it was recorded. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

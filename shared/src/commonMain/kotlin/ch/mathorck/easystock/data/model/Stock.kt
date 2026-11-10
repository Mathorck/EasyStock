package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
@Serializable
data class Location(
    @PrimaryKey val id: String,
    val name: String,
    /** Hierarchy: room > shelf > bin. */
    @SerialName("parent_id") @ColumnInfo(name = "parent_id") val parentId: String? = null,
    /** Key of [LocationIcons], null = default icon. Unknown keys (newer app) fall back to the default. */
    val icon: String? = null,
    /** Key of [LocationColors], null = default color. */
    val color: String? = null,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
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
@Serializable
data class Item(
    @PrimaryKey val id: String,
    @SerialName("article_id") @ColumnInfo(name = "article_id") val articleId: String,
    @SerialName("serial_number") @ColumnInfo(name = "serial_number") val serialNumber: String? = null,
    @ColumnInfo(defaultValue = "available") val status: ItemStatus = ItemStatus.AVAILABLE,
    /** Current location. Cache only, the movements are the source of truth. */
    @SerialName("location_id") @ColumnInfo(name = "location_id") val locationId: String? = null,
    /** Unit-specific values, same keys as [Article.attributes]. */
    val attributes: JsonObject? = null,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
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
@Serializable
data class StockMovement(
    @PrimaryKey val id: String,
    @SerialName("article_id") @ColumnInfo(name = "article_id") val articleId: String,
    /** Set when the article uses [Tracking.SERIAL]. */
    @SerialName("item_id") @ColumnInfo(name = "item_id") val itemId: String? = null,
    @SerialName("location_id") @ColumnInfo(name = "location_id") val locationId: String,
    /** + in / - out, in thousandths of the article unit (1.5 kg = 1500). */
    @SerialName("quantity") @ColumnInfo(name = "quantity") val quantityMilli: Long,
    val reason: MovementReason,
    val comment: String? = null,
    /** When it happened (may be offline). */
    @SerialName("occurred_at") @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    /** When it was recorded. */
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
)

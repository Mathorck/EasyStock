package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonObject

// Common conventions for every table:
// - id: UUID string, generated on the device
// - *_at: epoch milliseconds, UTC, set by the app
// - deleted_at: soft delete, needed to sync deletions

@Entity(
    tableName = "category",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["updated_at"]),
    ],
)
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "attribute_definition",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["updated_at"]),
    ],
)
data class AttributeDefinition(
    @PrimaryKey val id: String,
    /** Immutable once created, used as JSON key in `attributes`. */
    val key: String,
    val label: String,
    @ColumnInfo(name = "data_type") val dataType: AttributeDataType,
    /** e.g. mm, V, kg */
    val unit: String? = null,
    /** Allowed values when [dataType] is [AttributeDataType.ENUM]. */
    val options: List<String>? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "category_attribute",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["category_id"]),
        ForeignKey(entity = AttributeDefinition::class, parentColumns = ["id"], childColumns = ["attribute_id"]),
    ],
    indices = [
        Index(value = ["category_id", "attribute_id"], unique = true),
        Index(value = ["attribute_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class CategoryAttribute(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "attribute_id") val attributeId: String,
    @ColumnInfo(defaultValue = "0") val required: Boolean = false,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "article",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["category_id"]),
    ],
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["barcode"], unique = true),
        Index(value = ["category_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class Article(
    @PrimaryKey val id: String,
    /** Human-readable internal reference. */
    val sku: String,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(defaultValue = "quantity") val tracking: Tracking = Tracking.QUANTITY,
    /** pce, kg, m, l... */
    @ColumnInfo(defaultValue = "pce") val unit: String = "pce",
    /** NFC tag ID read by the scanner. One tag per article. */
    val barcode: String? = null,
    /** Reorder alert level, in thousandths of [unit] (1.5 kg = 1500). */
    @ColumnInfo(name = "threshold") val thresholdMilli: Long? = null,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    /** Values keyed by [AttributeDefinition.key]. */
    val attributes: JsonObject? = null,
    /** No longer used, kept for history. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
) {
    /** Soft delete. Clears [barcode] so the NFC tag can be reused on another article. */
    fun softDeleted(now: Long): Article = copy(barcode = null, deletedAt = now, updatedAt = now)
}

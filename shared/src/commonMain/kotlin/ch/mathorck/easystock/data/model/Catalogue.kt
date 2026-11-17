package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
@Serializable
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    /**
     * Name of its articles built from attribute values, e.g. "{rope_type} rope {diameter} {length}".
     * See [ch.mathorck.easystock.composables.attributes.parseNameTemplate]. Null: no template.
     */
    @SerialName("name_template") @ColumnInfo(name = "name_template") val nameTemplate: String? = null,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "attribute_definition",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["updated_at"]),
    ],
)
@Serializable
data class AttributeDefinition(
    @PrimaryKey val id: String,
    /** Immutable once created, used as JSON key in `attributes`. */
    val key: String,
    val label: String,
    @SerialName("data_type") @ColumnInfo(name = "data_type") val dataType: AttributeDataType,
    /** e.g. mm, V, kg */
    val unit: String? = null,
    /** Allowed values when [dataType] is [AttributeDataType.ENUM]. */
    val options: List<String>? = null,
    /** Date attributes only: an article or unit whose date is in the past is flagged. */
    @SerialName("flag_when_past") @ColumnInfo(name = "flag_when_past", defaultValue = "0") val flagWhenPast: Boolean = false,
    /** Date attributes only: a notification is sent when the date is reached. */
    @SerialName("notify_when_past") @ColumnInfo(name = "notify_when_past", defaultValue = "0") val notifyWhenPast: Boolean = false,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
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
@Serializable
data class CategoryAttribute(
    @PrimaryKey val id: String,
    @SerialName("category_id") @ColumnInfo(name = "category_id") val categoryId: String,
    @SerialName("attribute_id") @ColumnInfo(name = "attribute_id") val attributeId: String,
    @ColumnInfo(defaultValue = "0") val required: Boolean = false,
    @SerialName("sort_order") @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
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
@Serializable
data class Article(
    @PrimaryKey val id: String,
    /** Optional human-readable internal reference. Unique when set. */
    val sku: String? = null,
    val name: String,
    /**
     * True: [name] follows the template of the category and is rendered again when the template or the
     * attribute values change. False: typed by hand, never renamed.
     */
    @SerialName("name_auto") @ColumnInfo(name = "name_auto", defaultValue = "0") val nameAuto: Boolean = false,
    val description: String? = null,
    @SerialName("category_id") @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(defaultValue = "quantity") val tracking: Tracking = Tracking.QUANTITY,
    /** pce, kg, m, l... */
    @ColumnInfo(defaultValue = "pce") val unit: String = "pce",
    /** NFC tag ID read by the scanner, uppercase hex of the tag UID (see `nfc.tagIdOf`). One tag per article. */
    val barcode: String? = null,
    /** Reorder alert level, in thousandths of [unit] (1.5 kg = 1500). */
    @SerialName("threshold") @ColumnInfo(name = "threshold") val thresholdMilli: Long? = null,
    @SerialName("image_path") @ColumnInfo(name = "image_path") val imagePath: String? = null,
    /** Values keyed by [AttributeDefinition.key]. */
    val attributes: JsonObject? = null,
    /** Where to reorder the article (web address), null = none. */
    @SerialName("order_url") @ColumnInfo(name = "order_url") val orderUrl: String? = null,
    /** No longer used, kept for history. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
) {
    /** Soft delete. Clears [barcode] so the NFC tag can be reused on another article. */
    fun softDeleted(now: Long): Article = copy(barcode = null, deletedAt = now, updatedAt = now)
}

/**
 * A note raised on an article ("something is wrong"). Open while [resolvedAt] and [deletedAt] are null.
 * [createdBy] is set by the server (never sent by the phone, never changed by a local edit): it is stored
 * from the pulled row. Null in standalone mode.
 */
@Entity(
    tableName = "article_flag",
    foreignKeys = [
        ForeignKey(entity = Article::class, parentColumns = ["id"], childColumns = ["article_id"]),
    ],
    indices = [
        Index(value = ["article_id"]),
        Index(value = ["updated_at"]),
    ],
)
@Serializable
data class ArticleFlag(
    @PrimaryKey val id: String,
    @SerialName("article_id") @ColumnInfo(name = "article_id") val articleId: String,
    val note: String,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Server mode: the user who raised it, set by the server. Null in standalone mode. */
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    /** Null = open. */
    @SerialName("resolved_at") @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

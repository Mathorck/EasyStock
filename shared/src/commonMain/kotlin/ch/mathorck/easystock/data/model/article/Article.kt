package ch.mathorck.easystock.data.model.article

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.enums.article.Tracking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

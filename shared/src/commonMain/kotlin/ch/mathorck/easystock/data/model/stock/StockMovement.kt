package ch.mathorck.easystock.data.model.stock

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.stock.MovementReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        // Covering: stock sums of an article (SUM(quantity) per location or in total) read only the index
        Index(value = ["article_id", "location_id", "quantity"]),
        // Covering: stock sums of a location (and the location_id foreign key)
        Index(value = ["location_id", "article_id", "quantity"]),
        // Movement history of an article, newest first (ArticleDetailDao.observeMovements), no sort
        Index(value = ["article_id", "occurred_at", "created_at", "id"]),
        Index(value = ["item_id"]),
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
    /** Server mode: the user who recorded it, set by the server. Null in standalone mode and on Guest phones. */
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    /** Server mode: the device that recorded it, set by the server. Null in standalone mode and on Guest phones. */
    @SerialName("device_id") @ColumnInfo(name = "device_id") val deviceId: String? = null,
)

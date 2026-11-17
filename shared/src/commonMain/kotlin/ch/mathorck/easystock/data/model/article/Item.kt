package ch.mathorck.easystock.data.model.article

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.article.ItemStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

package ch.mathorck.easystock.data.model.review

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.review.ReviewStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

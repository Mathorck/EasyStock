package ch.mathorck.easystock.data.model.attributes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

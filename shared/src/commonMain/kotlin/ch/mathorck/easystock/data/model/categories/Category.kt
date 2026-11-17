package ch.mathorck.easystock.data.model.categories

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
     * See [ch.mathorck.easystock.services.attributes.parseNameTemplate]. Null: no template.
     */
    @SerialName("name_template") @ColumnInfo(name = "name_template") val nameTemplate: String? = null,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

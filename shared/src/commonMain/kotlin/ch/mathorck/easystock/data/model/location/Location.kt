package ch.mathorck.easystock.data.model.location

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "location",
    foreignKeys = [
        ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["parent_id"]),
    ],
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["barcode"], unique = true),
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
    /**
     * Optional NFC tag ID (see nfc/TagId), same format as [Article.barcode]. A tag is on one
     * article or one location, never both: checked by TagDao, SQLite can't across tables.
     */
    val barcode: String? = null,
) {
    /** Soft delete. Clears [barcode] so the tag can be linked again. */
    fun softDeleted(now: Long): Location = copy(barcode = null, deletedAt = now, updatedAt = now)
}

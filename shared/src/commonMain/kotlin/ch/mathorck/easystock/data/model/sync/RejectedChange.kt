package ch.mathorck.easystock.data.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A local change the server did not accept, for the "Changes not accepted" list. */
@Entity(tableName = "rejected_change")
data class RejectedChange(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** Reason code given by the server. */
    val reason: String,
    @ColumnInfo(name = "rejected_at") val rejectedAt: Long,
    /** When the user saw it, null = counted in the Settings badge. */
    @ColumnInfo(name = "seen_at") val seenAt: Long? = null,
)

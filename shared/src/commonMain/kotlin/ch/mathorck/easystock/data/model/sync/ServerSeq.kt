package ch.mathorck.easystock.data.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The `server_seq` of the server version of a synced row this phone holds (v11, every synced table
 * except `stock_movement`). From the pull and the push results; sent back as `baseSeq` when the row
 * is edited or soft-deleted (first change wins). No row: the server never had it, pushed without `baseSeq`.
 */
@Entity(tableName = "server_seq", primaryKeys = ["table_name", "row_id"])
data class ServerSeq(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    val seq: Long,
)

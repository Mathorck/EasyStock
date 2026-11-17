package ch.mathorck.easystock.data.dao.sync

import androidx.room.ColumnInfo
import ch.mathorck.easystock.services.sync.rowId

/** One `server_seq` row, read by [SyncDao.serverSeqs]. */
data class RowSeq(
    @ColumnInfo(name = "row_id") val rowId: String,
    val seq: Long,
)

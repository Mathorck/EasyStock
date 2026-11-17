package ch.mathorck.easystock.data.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** A local change waiting to be pushed. Written by the triggers only, one row per changed row. */
@Entity(
    tableName = "outbox",
    primaryKeys = ["table_name", "row_id"],
    indices = [Index(value = ["changed_at"])],
)
data class OutboxEntry(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** First change since the last push, epoch milliseconds UTC. */
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)

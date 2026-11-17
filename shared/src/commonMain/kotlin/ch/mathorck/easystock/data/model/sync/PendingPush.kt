package ch.mathorck.easystock.data.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.server.PENDING_PUSH_ID

/**
 * The push batch sent and not answered yet (v10, at most one row). After a network failure the same
 * [batchId] and [body] are sent again, so the server applies the batch once and gives the same answer.
 */
@Entity(tableName = "pending_push")
data class PendingPush(
    @PrimaryKey val id: Int = PENDING_PUSH_ID,
    @ColumnInfo(name = "batch_id") val batchId: String,
    /** The exact JSON request body. */
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

package ch.mathorck.easystock.data.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.server.SYNC_STATE_ID

/**
 * The server this phone is connected to. One row ([SYNC_STATE_ID]) while connected, none in standalone mode.
 * Its presence turns the outbox triggers on (see data/db/OutboxTriggers).
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Int = SYNC_STATE_ID,
    @ColumnInfo(name = "server_url") val serverUrl: String,
    /** SHA-256 of the self-signed server certificate, lowercase hex. Null for HTTP or a trusted certificate. */
    @ColumnInfo(name = "cert_fingerprint") val certFingerprint: String? = null,
    @ColumnInfo(name = "user_id") val userId: String,
    /** [ch.mathorck.easystock.enums.auth.Role.dbValue]. */
    val role: String,
    /** Pull cursor given by the server, 0 = pull everything. */
    @ColumnInfo(defaultValue = "0") val since: Long = 0,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    /** True while the sync engine writes pulled rows: the outbox triggers do nothing. */
    @ColumnInfo(defaultValue = "0") val applying: Boolean = false,
)

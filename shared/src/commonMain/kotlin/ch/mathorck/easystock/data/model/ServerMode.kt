package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Server mode tables (schema v9). App only: never synced, never in a backup, empty in standalone mode.
// See docs/schema.dbml and docs/server-mode/02-app.md.

/** Id of the single [SyncState] row. */
const val SYNC_STATE_ID = 1

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
    /** [ch.mathorck.easystock.auth.Role.dbValue]. */
    val role: String,
    /** Pull cursor given by the server, 0 = pull everything. */
    @ColumnInfo(defaultValue = "0") val since: Long = 0,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    /** True while the sync engine writes pulled rows: the outbox triggers do nothing. */
    @ColumnInfo(defaultValue = "0") val applying: Boolean = false,
)

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

/** A user of the server, to show who made each movement. */
@Entity(tableName = "server_user")
data class ServerUser(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)

/** A low-stock alert received from the server. Never created on the phone. No foreign key: it may arrive before its article. */
@Entity(
    tableName = "low_stock_alert",
    indices = [Index(value = ["article_id"]), Index(value = ["notified"])],
)
data class LowStockAlert(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "crossed_at") val crossedAt: Long,
    /** Stock at that time, in thousandths of the article unit. */
    @ColumnInfo(name = "stock") val stockMilli: Long,
    @ColumnInfo(defaultValue = "0") val notified: Boolean = false,
)

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

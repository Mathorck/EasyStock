package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import kotlinx.coroutines.flow.Flow

/** The server session (`sync_state`) and the checks around joining and leaving. Empty in standalone mode. */
@Dao
abstract class SessionDao {
    /** `sync_state.role`, null when there is no `sync_state` row (standalone mode). */
    @Query("SELECT role FROM sync_state LIMIT 1")
    abstract fun observeRole(): Flow<String?>

    /** The `sync_state` row, null in standalone mode. The server client is only built from it. */
    @Query("SELECT * FROM sync_state LIMIT 1")
    abstract fun observeSyncState(): Flow<SyncState?>

    @Query("SELECT * FROM sync_state LIMIT 1")
    abstract suspend fun syncState(): SyncState?

    /**
     * Rows of every synced table, soft-deleted rows included: the empty-phone rule (a phone joins a server
     * only when this is 0, see auth/EmptyPhoneCheck).
     */
    @Query(
        """SELECT (SELECT COUNT(*) FROM category) + (SELECT COUNT(*) FROM attribute_definition)
        + (SELECT COUNT(*) FROM category_attribute) + (SELECT COUNT(*) FROM location)
        + (SELECT COUNT(*) FROM article) + (SELECT COUNT(*) FROM item)
        + (SELECT COUNT(*) FROM stock_movement) + (SELECT COUNT(*) FROM stock_review)
        + (SELECT COUNT(*) FROM stock_review_line)""",
    )
    abstract suspend fun syncedRowCount(): Long

    /** Local changes waiting to be pushed: checked before logging out. */
    @Query("SELECT COUNT(*) FROM outbox")
    abstract suspend fun outboxCount(): Int

    /** Display name of a server user (the own one for the Account screen), null when unknown. */
    @Query("SELECT display_name FROM server_user WHERE user_id = :userId")
    abstract fun observeDisplayName(userId: String): Flow<String?>

    @Query("UPDATE sync_state SET role = :role")
    abstract suspend fun updateRole(role: String)

    /** A Guest promoted to a member account: new role, and the next sync pulls everything again. */
    @Query("UPDATE sync_state SET role = :role, since = 0")
    abstract suspend fun promoted(role: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSyncState(state: SyncState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertServerUser(user: ServerUser)

    /**
     * Joins the server: writes [state] and the own [user] in one transaction, only when the synced tables
     * are still empty and no server is joined yet. False (nothing written) otherwise.
     */
    @Transaction
    open suspend fun join(state: SyncState, user: ServerUser): Boolean {
        if (syncState() != null || syncedRowCount() > 0) return false
        upsertSyncState(state)
        upsertServerUser(user)
        return true
    }
}

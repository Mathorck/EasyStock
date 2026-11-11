package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Reads of the server session (`sync_state`). Empty in standalone mode. */
@Dao
interface SessionDao {
    /** `sync_state.role`, null when there is no `sync_state` row (standalone mode). */
    @Query("SELECT role FROM sync_state LIMIT 1")
    fun observeRole(): Flow<String?>
}

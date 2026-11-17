package ch.mathorck.easystock.data.dao.stock

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.stock.StockMovement
import kotlinx.coroutines.flow.Flow

/** Locations and stock movements. Movements are append-only: insert, never update or delete. */
@Dao
interface StockDao {
    @Insert
    suspend fun insertLocation(location: Location)

    /** All locations (flat list, use parentId for the hierarchy), sorted by name. */
    @Query("SELECT * FROM location WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE, id")
    fun observeLocations(): Flow<List<Location>>

    /** A transfer is two movements: one negative, one positive. */
    @Insert
    suspend fun insertMovement(movement: StockMovement)
}

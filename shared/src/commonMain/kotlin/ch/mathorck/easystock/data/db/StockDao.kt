package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement

/** Locations and stock movements. Movements are append-only: insert, never update or delete. */
@Dao
interface StockDao {
    @Insert
    suspend fun insertLocation(location: Location)

    /** A transfer is two movements: one negative, one positive. */
    @Insert
    suspend fun insertMovement(movement: StockMovement)
}

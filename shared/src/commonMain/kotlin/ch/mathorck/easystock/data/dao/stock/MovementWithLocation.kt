package ch.mathorck.easystock.data.dao.stock

import androidx.room.ColumnInfo
import androidx.room.Embedded
import ch.mathorck.easystock.data.model.stock.StockMovement

/**
 * A movement with the name of its location, and the display name of its author (server mode only:
 * null when `created_by` is null, always in standalone mode, or when the user is not in `server_user`).
 */
data class MovementWithLocation(
    @Embedded val movement: StockMovement,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "author_name") val authorName: String? = null,
)

package ch.mathorck.easystock.data.dao.article

import androidx.room.ColumnInfo

/** Location of the last movement of a serial unit. */
data class ItemLastLocation(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "location_id") val locationId: String,
)

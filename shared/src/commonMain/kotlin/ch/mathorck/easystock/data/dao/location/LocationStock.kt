package ch.mathorck.easystock.data.dao.location

import androidx.room.ColumnInfo

/** Stock of one article at one location, in thousandths of the article unit. */
data class LocationStock(
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "stock") val stockMilli: Long,
)

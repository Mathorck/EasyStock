package ch.mathorck.easystock.data.dao.location

import androidx.room.ColumnInfo

/** Last finished review per root location. */
data class LocationReviewedRow(
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
)

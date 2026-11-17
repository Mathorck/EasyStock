package ch.mathorck.easystock.data.dao.article

import androidx.room.ColumnInfo

/**
 * A review line of the article flagged "info to fix", from a finished review. Open while [fixedAt] is null.
 * [serialNumber] is set for a serial unit line.
 */
data class InfoFlag(
    @ColumnInfo(name = "line_id") val lineId: String,
    val note: String?,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    /** When the review was finished: the flag is known from then on. */
    @ColumnInfo(name = "finished_at") val flaggedAt: Long,
    @ColumnInfo(name = "info_fixed_at") val fixedAt: Long?,
)

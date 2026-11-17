package ch.mathorck.easystock.data.dao.review

import androidx.room.ColumnInfo
import ch.mathorck.easystock.enums.review.ReviewStatus

/** A review with its counters, for the list of reviews. */
data class ReviewSummaryRow(
    val id: String,
    @ColumnInfo(name = "location_id") val locationId: String,
    val status: ReviewStatus,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    val total: Int,
    val checked: Int,
    val discrepancies: Int,
    val flagged: Int,
)

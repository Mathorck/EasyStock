package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.data.dao.review.ReviewSummaryRow

// ---------- List of reviews ----------

data class ReviewListRow(val summary: ReviewSummaryRow, val path: String, val locationDeleted: Boolean)

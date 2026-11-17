package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.data.model.review.StockReviewLine
import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.review.LineStatus
import ch.mathorck.easystock.services.review.status

// ---------- One review ----------

data class ReviewLineUi(
    val line: StockReviewLine,
    val articleName: String,
    val unit: String,
    val serialNumber: String?,
    val itemStatus: ItemStatus?,
    val attributes: String,
    val status: LineStatus,
    /** Path of [StockReviewLine.foundLocationId], for a unit found elsewhere. */
    val foundPath: String?,
    val articleDeleted: Boolean,
)

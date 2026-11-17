package ch.mathorck.easystock.services.review

import ch.mathorck.easystock.data.model.review.StockReviewLine

/** Changes to bring the lines of a review in line with the current stock (see [syncReviewLines]). */
data class LineSync(val inserts: List<StockReviewLine>, val updates: List<StockReviewLine>)

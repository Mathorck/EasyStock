package ch.mathorck.easystock.services.review

import ch.mathorck.easystock.data.model.review.StockReviewLine

/** Data the finish plan reads, loaded in the same transaction that applies it. */
data class FinishInputs(
    val lines: List<StockReviewLine>,
    /** Current SUM(quantity) per (article, location) of the quantity lines. */
    val stock: Map<Pair<String, String>, Long>,
    val units: Map<String, UnitState>,
    val activeArticleIds: Set<String>,
    val activeLocationIds: Set<String>,
    /** Every serial number in use (lowercase), soft-deleted units included. */
    val takenSerials: Set<String> = emptySet(),
)

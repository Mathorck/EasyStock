package ch.mathorck.easystock.services.review

/** Counters of a review: [checked] / [total], with [discrepancies] and [flagged] among them. */
data class ReviewProgress(val total: Int, val checked: Int, val discrepancies: Int, val flagged: Int)

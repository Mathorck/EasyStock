package ch.mathorck.easystock.viewmodels.article

/** One entry of the article history: a stock movement, or an "info to fix" flag raised or fixed. */
sealed interface HistoryRow {
    /** Unique among the rows, for the list keys. */
    val key: String

    data class Movement(val movement: MovementRow) : HistoryRow {
        override val key: String get() = "movement-${movement.id}"
    }

    /** Flag raised in a review, dated when the review was finished. */
    data class InfoFlagged(val flag: InfoFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "flagged-${flag.lineId}"
    }

    /** Flag marked as fixed. */
    data class InfoFixed(val flag: InfoFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "fixed-${flag.lineId}"
    }

    /** Manual flag ("Report a problem"), dated when it was raised. */
    data class ProblemReported(val flag: ManualFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "problem-reported-${flag.id}"
    }

    /** Manual flag resolved. */
    data class ProblemFixed(val flag: ManualFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "problem-fixed-${flag.id}"
    }
}

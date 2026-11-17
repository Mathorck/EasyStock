package ch.mathorck.easystock.viewmodels.article

/** One card of the open flags: a manual flag or an "info to fix" flag from a review. */
sealed interface FlagCard {
    /** Unique among the cards, for the list keys. */
    val key: String

    data class Review(val row: InfoFlagRow) : FlagCard {
        override val key: String get() = "info-flag-${row.lineId}"
    }

    data class Manual(val row: ManualFlagRow) : FlagCard {
        override val key: String get() = "problem-${row.id}"
    }
}

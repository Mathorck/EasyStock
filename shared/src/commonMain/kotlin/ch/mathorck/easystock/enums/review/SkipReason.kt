package ch.mathorck.easystock.enums.review

/** Why a checked line with a discrepancy is not applied. */
enum class SkipReason {
    /** The article, unit or location was deleted since. */
    GONE,
    /** The unit changed since the review saw it (moved, lent or lost meanwhile), the result no longer applies. */
    CHANGED,
    /** The serial number of a new unit was taken since (by another unit or another new unit of the review). */
    DUPLICATE_SERIAL,
}

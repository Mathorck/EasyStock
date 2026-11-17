package ch.mathorck.easystock.services.sync

/** `reason` values of a rejected row (server SyncPushReasonHelper). Anything else is shown as is. */
object RejectReasons {
    const val NOT_ALLOWED = "not-allowed"
    const val INVALID = "invalid"
    const val MISSING_REFERENCE = "missing-reference"
    const val REVIEW_OPEN = "review-open"
    const val CONFLICT = CONFLICT_REASON

    val ALL = listOf(NOT_ALLOWED, INVALID, MISSING_REFERENCE, REVIEW_OPEN, CONFLICT)
}

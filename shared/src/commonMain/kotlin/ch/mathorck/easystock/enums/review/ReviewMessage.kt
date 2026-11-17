package ch.mathorck.easystock.enums.review

/** One-shot feedback of the review screens, shown once as a snackbar. */
enum class ReviewMessage {
    CLOSED, FAILED, FINISHED, TAG_FAILED, TAG_UNKNOWN, TAG_NOT_IN_REVIEW,

    /** A location tag outside the review scope. */
    TAG_LOCATION_OUTSIDE,

    /** A location tag inside the scope, but no line there (with the current filter none at all). */
    TAG_LOCATION_EMPTY,
}

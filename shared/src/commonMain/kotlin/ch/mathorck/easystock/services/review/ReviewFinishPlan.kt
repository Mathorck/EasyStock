package ch.mathorck.easystock.services.review

/** Result of [planReviewFinish]: shown in the summary, then applied as is. */
data class ReviewFinishPlan(
    val changes: List<ReviewChange>,
    val skipped: List<SkippedLine>,
    /** Quantity lines whose stock moved between the count and now (the correction is kept relative). */
    val drifted: Set<String>,
    /** Quantity lines whose correction would go below 0: the stock is set to 0 instead. */
    val clamped: Set<String>,
    val unchecked: Int,
)

package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.enums.review.MissingResolution
import ch.mathorck.easystock.services.review.ReviewFinishPlan

/** The finish summary, computed before anything is written. */
data class ReviewFinishPreview(
    val plan: ReviewFinishPlan,
    val resolutions: Map<String, MissingResolution>,
    /** Lines marked missing: the user chooses lost or leave for each. */
    val missingLineIds: List<String>,
    val finishing: Boolean = false,
)

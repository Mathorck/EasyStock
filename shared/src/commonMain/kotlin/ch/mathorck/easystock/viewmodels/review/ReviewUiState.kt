package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.data.model.review.StockReview
import ch.mathorck.easystock.enums.review.LineFilter
import ch.mathorck.easystock.enums.review.ReviewStatus
import ch.mathorck.easystock.services.review.ReviewProgress
import ch.mathorck.easystock.services.review.progress
import ch.mathorck.easystock.services.review.status
import ch.mathorck.easystock.viewmodels.location.LocationOption

data class ReviewUiState(
    val loading: Boolean = true,
    /** Null once loaded: the review is gone. */
    val review: StockReview? = null,
    val rootName: String = "",
    val rootPath: String = "",
    val groups: List<ReviewGroup> = emptyList(),
    val progress: ReviewProgress = ReviewProgress(0, 0, 0, 0),
    val filter: LineFilter = LineFilter.ALL,
    /** Active locations of the review scope (where a found item can be), as a tree. */
    val scopeLocations: List<LocationOption> = emptyList(),
    /** All active locations (where a unit can be found elsewhere), as a tree. */
    val allLocations: List<LocationOption> = emptyList(),
    val paths: Map<String, String> = emptyMap(),
    val lines: Map<String, ReviewLineUi> = emptyMap(),
) {
    val isOpen: Boolean get() = review?.status == ReviewStatus.OPEN
}

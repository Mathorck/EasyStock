package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.viewmodels.location.LocationOption

data class ReviewListUiState(
    val loading: Boolean = true,
    val rows: List<ReviewListRow> = emptyList(),
    /** Active locations a review can start on, as a tree. */
    val locations: List<LocationOption> = emptyList(),
)

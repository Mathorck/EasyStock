package ch.mathorck.easystock.viewmodels.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dao.review.ReviewDao
import ch.mathorck.easystock.enums.review.ReviewMessage
import ch.mathorck.easystock.viewmodels.location.buildLocationOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings > Stock reviews: open and finished reviews, start a new one. */
class ReviewListViewModel(private val dao: ReviewDao) : ViewModel() {
    val state: StateFlow<ReviewListUiState> = combine(dao.observeReviews(), dao.observeAllLocations()) { reviews, locations ->
        val paths = buildLocationOptions(locations).associate { it.id to it.path }
        val deleted = locations.filter { it.deletedAt != null }.map { it.id }.toSet()
        ReviewListUiState(
            loading = false,
            rows = reviews.map { ReviewListRow(it, paths[it.locationId] ?: "", it.locationId in deleted) },
            locations = buildLocationOptions(locations.filter { it.deletedAt == null }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewListUiState())

    val opened = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<ReviewMessage?>(null)

    fun start(locationId: String) {
        viewModelScope.launch {
            val id = startOrResumeReview(dao, locationId)
            if (id == null) message.value = ReviewMessage.FAILED else opened.value = id
        }
    }

    fun consumeOpened() {
        opened.value = null
    }

    fun consumeMessage() {
        message.value = null
    }
}

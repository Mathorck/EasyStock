package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.attributes.summaryLine
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.FoundCandidate
import ch.mathorck.easystock.data.db.ReviewClosedException
import ch.mathorck.easystock.data.db.ReviewDao
import ch.mathorck.easystock.data.db.ReviewSummaryRow
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.data.model.ReviewStatus
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.nfc.TagEvent
import ch.mathorck.easystock.review.LineFilter
import ch.mathorck.easystock.review.LineStatus
import ch.mathorck.easystock.review.MissingResolution
import ch.mathorck.easystock.review.ReviewFinishPlan
import ch.mathorck.easystock.review.ReviewProgress
import ch.mathorck.easystock.review.isSerial
import ch.mathorck.easystock.review.matches
import ch.mathorck.easystock.review.progress
import ch.mathorck.easystock.review.status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One-shot feedback of the review screens, shown once as a snackbar. */
enum class ReviewMessage { CLOSED, FAILED, FINISHED, TAG_FAILED, TAG_UNKNOWN, TAG_NOT_IN_REVIEW }

/** Starts a review on [locationId], or resumes its open one. Returns the review id, null on failure. */
@OptIn(ExperimentalUuidApi::class)
suspend fun startOrResumeReview(dao: ReviewDao, locationId: String, clock: Clock = Clock.System): String? = try {
    dao.startReview(locationId, clock.now().toEpochMilliseconds()) { Uuid.random().toString() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

// ---------- List of reviews ----------

data class ReviewListRow(val summary: ReviewSummaryRow, val path: String, val locationDeleted: Boolean)

data class ReviewListUiState(
    val loading: Boolean = true,
    val rows: List<ReviewListRow> = emptyList(),
    /** Active locations a review can start on, as a tree. */
    val locations: List<LocationOption> = emptyList(),
)

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

// ---------- One review ----------

data class ReviewLineUi(
    val line: StockReviewLine,
    val articleName: String,
    val unit: String,
    val serialNumber: String?,
    val itemStatus: ItemStatus?,
    val attributes: String,
    val status: LineStatus,
    /** Path of [StockReviewLine.foundLocationId], for a unit found elsewhere. */
    val foundPath: String?,
    val articleDeleted: Boolean,
)

data class ReviewGroup(val locationId: String, val name: String, val path: String, val lines: List<ReviewLineUi>)

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

/** The finish summary, computed before anything is written. */
data class ReviewFinishPreview(
    val plan: ReviewFinishPlan,
    val resolutions: Map<String, MissingResolution>,
    /** Lines marked missing: the user chooses lost or leave for each. */
    val missingLineIds: List<String>,
    val finishing: Boolean = false,
)

data class FoundSearchState(val query: String = "", val results: List<FoundCandidate> = emptyList(), val searching: Boolean = false)

/** Checklist of a review: count, mark, flag, add found items, then finish. */
@OptIn(ExperimentalUuidApi::class)
class ReviewViewModel(
    private val reviewId: String,
    private val dao: ReviewDao,
    attributeDao: AttributeDao,
    /** Active article of an NFC tag ID (`article.barcode`). */
    private val findArticleByTag: suspend (String) -> Article? = { null },
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private val filter = MutableStateFlow(LineFilter.ALL)
    val preview = MutableStateFlow<ReviewFinishPreview?>(null)
    val search = MutableStateFlow(FoundSearchState())
    val message = MutableStateFlow<ReviewMessage?>(null)
    private var searchJob: Job? = null

    /** Line to scroll to after a tag scan, consumed by the screen. */
    val jumpTo = MutableStateFlow<String?>(null)

    /** True when a scanned article is not in the review: the screen opens the "found item" search. */
    val openFound = MutableStateFlow(false)

    private val catalog = combine(attributeDao.observeDefinitions(), attributeDao.observeActiveLinks(), ::AttributeCatalog)

    private val data = combine(
        dao.observeReview(reviewId),
        dao.observeLines(reviewId),
        dao.observeReviewArticles(reviewId),
        dao.observeReviewItems(reviewId),
        dao.observeAllLocations(),
    ) { review, lines, articles, items, locations -> ReviewData(review, lines, articles, items, locations) }

    val state: StateFlow<ReviewUiState> = combine(data, catalog, filter) { data, catalog, filter ->
        buildState(data, catalog, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        // Stock added or moved since the last visit gets its line
        write { dao.syncLines(reviewId, now(), newId) }
    }

    fun setFilter(value: LineFilter) {
        filter.value = value
    }

    fun sync() = write { dao.syncLines(reviewId, now(), newId) }

    fun setCount(lineId: String, countedMilli: Long) = write { dao.setCount(lineId, countedMilli.coerceAtLeast(0), now()) }

    fun markCorrect(lineId: String) = write { dao.markCorrect(lineId, now()) }

    fun setUnitResult(lineId: String, result: ReviewResult, foundLocationId: String? = null) =
        write { dao.setUnitResult(lineId, result, foundLocationId, now()) }

    fun reset(lineId: String) = write { dao.resetLine(lineId, now()) }

    fun setFlag(lineId: String, flagged: Boolean, note: String?) = write { dao.setFlag(lineId, flagged, note, now()) }

    fun cancelReview(onDone: () -> Unit) = write {
        dao.cancelReview(reviewId, now())
        onDone()
    }

    // ---------- Found items ----------

    fun onSearchChange(query: String) {
        search.value = search.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            search.value = FoundSearchState(query)
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            search.value = search.value.copy(searching = true)
            val results = try {
                dao.searchFoundCandidates(query.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            search.value = FoundSearchState(query, results)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        search.value = FoundSearchState()
    }

    fun addFound(candidate: FoundCandidate, locationId: String) = write {
        val itemId = candidate.itemId
        if (itemId != null) {
            dao.addFoundUnit(reviewId, itemId, locationId, now(), newId)
        } else if (candidate.tracking == Tracking.QUANTITY) {
            dao.addFoundArticle(reviewId, candidate.articleId, locationId, now(), newId)
        }
        clearSearch()
    }

    // ---------- Finish ----------

    /** Opens the summary. Missing units are lost by default. Nothing is written. */
    fun requestFinish() {
        val missing = state.value.groups.flatMap { it.lines }
            .filter { it.line.result == ReviewResult.MISSING }.map { it.line.id }
        planWith(missing.associateWith { MissingResolution.LOSE }, missing)
    }

    fun setResolution(lineId: String, resolution: MissingResolution) {
        val current = preview.value ?: return
        planWith(current.resolutions + (lineId to resolution), current.missingLineIds)
    }

    fun dismissFinish() {
        preview.value = null
    }

    fun confirmFinish(comment: String, onDone: () -> Unit) {
        val current = preview.value ?: return
        if (current.finishing) return
        preview.value = current.copy(finishing = true)
        viewModelScope.launch {
            try {
                dao.finishReview(reviewId, current.resolutions, comment, now(), newId)
                preview.value = null
                message.value = ReviewMessage.FINISHED
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReviewClosedException) {
                preview.value = null
                message.value = ReviewMessage.CLOSED
            } catch (e: Exception) {
                preview.value = current.copy(finishing = false)
                message.value = ReviewMessage.FAILED
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    // ---------- NFC ----------

    /** A scanned tag jumps to the line of its article (the first unchecked one), all lines shown. */
    fun onTag(event: TagEvent) {
        when (event) {
            TagEvent.Failed -> message.value = ReviewMessage.TAG_FAILED
            is TagEvent.Read -> viewModelScope.launch {
                val article = try {
                    findArticleByTag(event.tagId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (article == null) {
                    message.value = ReviewMessage.TAG_UNKNOWN
                    return@launch
                }
                val lines = state.value.lines.values.filter { it.line.articleId == article.id }
                val target = lines.firstOrNull { it.status == LineStatus.UNCHECKED } ?: lines.firstOrNull()
                if (target == null) {
                    message.value = ReviewMessage.TAG_NOT_IN_REVIEW
                    if (state.value.isOpen) {
                        onSearchChange(article.name)
                        openFound.value = true
                    }
                } else {
                    filter.value = LineFilter.ALL
                    jumpTo.value = target.line.id
                }
            }
        }
    }

    fun consumeJump() {
        jumpTo.value = null
    }

    fun consumeOpenFound() {
        openFound.value = false
    }

    private fun planWith(resolutions: Map<String, MissingResolution>, missing: List<String>) {
        viewModelScope.launch {
            try {
                preview.value = ReviewFinishPreview(dao.planFinish(reviewId, resolutions), resolutions, missing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReviewClosedException) {
                preview.value = null
                message.value = ReviewMessage.CLOSED
            } catch (e: Exception) {
                message.value = ReviewMessage.FAILED
            }
        }
    }

    private fun now() = clock.now().toEpochMilliseconds()

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReviewClosedException) {
                message.value = ReviewMessage.CLOSED
            } catch (e: Exception) {
                message.value = ReviewMessage.FAILED
            }
        }
    }
}

private data class ReviewData(
    val review: StockReview?,
    val lines: List<StockReviewLine>,
    val articles: List<Article>,
    val items: List<Item>,
    val locations: List<Location>,
)

private fun buildState(data: ReviewData, catalog: AttributeCatalog, filter: LineFilter): ReviewUiState {
    val review = data.review ?: return ReviewUiState(loading = false, filter = filter)
    val options = buildLocationOptions(data.locations)
    val paths = options.associate { it.id to it.path }
    val order = options.withIndex().associate { it.value.id to it.index }
    val byLocation = data.locations.associateBy { it.id }
    val articles = data.articles.associateBy { it.id }
    val items = data.items.associateBy { it.id }
    val active = data.locations.filter { it.deletedAt == null }
    val scope = active.subtreeIds(review.locationId).toSet()

    val uiLines = data.lines.map { line ->
        val article = articles[line.articleId]
        val item = line.itemId?.let { items[it] }
        val attributes = when {
            article == null -> ""
            // Unit values first, then the article's own
            item != null -> listOf(
                catalog.format(article.copy(attributes = item.attributes)).summaryLine(),
                catalog.format(article).summaryLine(),
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            else -> catalog.format(article).summaryLine()
        }
        ReviewLineUi(
            line = line,
            articleName = article?.name ?: "",
            unit = article?.unit ?: "",
            serialNumber = item?.serialNumber,
            itemStatus = item?.status,
            attributes = attributes,
            status = line.status(),
            foundPath = line.foundLocationId?.let { paths[it] },
            articleDeleted = article?.deletedAt != null,
        )
    }
    val groups = uiLines.filter { it.line.matches(filter) }
        .groupBy { it.line.locationId }
        .map { (locationId, lines) ->
            ReviewGroup(
                locationId = locationId,
                name = byLocation[locationId]?.name ?: "",
                path = paths[locationId] ?: "",
                lines = lines.sortedWith(
                    compareBy<ReviewLineUi> { it.articleName.lowercase() }.thenBy { it.serialNumber?.lowercase() ?: "" }.thenBy { it.line.id },
                ),
            )
        }
        .sortedBy { order[it.locationId] ?: Int.MAX_VALUE }
    val activeOptions = buildLocationOptions(active)
    return ReviewUiState(
        loading = false,
        review = review,
        rootName = byLocation[review.locationId]?.name ?: "",
        rootPath = paths[review.locationId] ?: "",
        groups = groups,
        progress = data.lines.progress(),
        filter = filter,
        scopeLocations = activeOptions.filter { it.id in scope },
        allLocations = activeOptions,
        paths = paths,
        lines = uiLines.associateBy { it.line.id },
    )
}

/** Last finished review of each location: its own, or the latest of an ancestor (a review covers the subtree). */
fun lastReviewedByLocation(locations: List<Location>, finished: Map<String, Long>): Map<String, Long> {
    val byId = locations.associateBy { it.id }
    return locations.associate { location ->
        var latest: Long? = null
        var current: Location? = location
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.id)) {
            finished[current.id]?.let { latest = maxOf(latest ?: it, it) }
            current = current.parentId?.let { byId[it] }
        }
        location.id to latest
    }.filterValues { it != null }.mapValues { it.value!! }
}

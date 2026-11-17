package ch.mathorck.easystock.viewmodels.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.summaryLine
import ch.mathorck.easystock.data.dao.article.FoundCandidate
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.review.ReviewDao
import ch.mathorck.easystock.data.dao.tags.TagOwner
import ch.mathorck.easystock.data.model.article.PendingUnit
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.review.pendingUnit
import ch.mathorck.easystock.enums.article.StockActionKind
import ch.mathorck.easystock.enums.article.Tracking
import ch.mathorck.easystock.enums.location.subtreeIds
import ch.mathorck.easystock.enums.review.LineFilter
import ch.mathorck.easystock.enums.review.LineStatus
import ch.mathorck.easystock.enums.review.MissingResolution
import ch.mathorck.easystock.enums.review.ReviewMessage
import ch.mathorck.easystock.enums.review.ReviewResult
import ch.mathorck.easystock.exceptions.review.ReviewClosedException
import ch.mathorck.easystock.exceptions.stock.DuplicateSerialException
import ch.mathorck.easystock.services.attributes.AttributeCatalog
import ch.mathorck.easystock.services.nfc.TagEvent
import ch.mathorck.easystock.services.review.matches
import ch.mathorck.easystock.services.review.progress
import ch.mathorck.easystock.services.review.status
import ch.mathorck.easystock.viewmodels.article.StockActionContext
import ch.mathorck.easystock.viewmodels.article.StockActionForm
import ch.mathorck.easystock.viewmodels.article.StockActionUiState
import ch.mathorck.easystock.viewmodels.article.validate
import ch.mathorck.easystock.viewmodels.location.LocationOption
import ch.mathorck.easystock.viewmodels.location.buildLocationOptions
import ch.mathorck.easystock.viewmodels.stock.FoundSearchState
import ch.mathorck.easystock.viewmodels.stock.SerialIndex
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The group to scroll to for a scanned location tag: the location's own group, else the first group of
 * one of its sub-locations (groups are in tree order). Null when the location has no line in the review.
 * [scope]: the review locations as a tree (parents followed by their children, see [buildLocationOptions]).
 */
fun groupForLocation(locationId: String, scope: List<LocationOption>, groups: List<ReviewGroup>): String? {
    val start = scope.indexOfFirst { it.id == locationId }
    if (start < 0) return null
    val depth = scope[start].depth
    val ids = HashSet<String>().apply {
        add(locationId)
        for (i in start + 1 until scope.size) {
            if (scope[i].depth <= depth) break
            add(scope[i].id)
        }
    }
    return groups.firstOrNull { it.locationId in ids }?.locationId
}

/** Starts a review on [locationId], or resumes its open one. Returns the review id, null on failure. */
@OptIn(ExperimentalUuidApi::class)
suspend fun startOrResumeReview(dao: ReviewDao, locationId: String, clock: Clock = Clock.System): String? = try {
    dao.startReview(locationId, clock.now().toEpochMilliseconds()) { Uuid.random().toString() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Checklist of a review: count, mark, flag, add found items, then finish. */
@OptIn(ExperimentalUuidApi::class)
class ReviewViewModel(
    private val reviewId: String,
    private val dao: ReviewDao,
    private val attributeDao: AttributeDao,
    /** Active article or location of an NFC tag ID (`barcode`). */
    private val findTagOwner: suspend (String) -> TagOwner? = { null },
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

    /** Location group (its id) to scroll to after a location tag scan, consumed by the screen. */
    val jumpToGroup = MutableStateFlow<String?>(null)

    /** True when a scanned article is not in the review: the screen opens the "found item" search. */
    val openFound = MutableStateFlow(false)

    private val newUnitDraft = MutableStateFlow<NewUnitDraft?>(null)

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

    /** Null when the form is closed. Pending units of the review count as taken serials. */
    val newUnit: StateFlow<NewUnitSheetState?> = combine(newUnitDraft, state) { draft, review ->
        draft?.let { buildNewUnit(it, review) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    // ---------- New unit found ----------

    /** Opens the add-unit form for a serial article found at [locationId]. The unit is only stored on the review. */
    fun openNewUnit(articleId: String, locationId: String) {
        viewModelScope.launch {
            try {
                val article = dao.getActiveArticle(articleId)?.takeIf { it.tracking == Tracking.SERIAL }
                if (article == null) {
                    message.value = ReviewMessage.FAILED
                    return@launch
                }
                val attributes = article.categoryId?.let { attributeDao.observeCategoryAttributes(it).first() }.orEmpty()
                newUnitDraft.value = NewUnitDraft(
                    article = article,
                    locationId = locationId,
                    form = StockActionForm(kind = StockActionKind.ADD_UNIT, targetLocationId = locationId),
                    itemAttributes = attributes,
                    serials = SerialIndex(dao.allSerials()),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = ReviewMessage.FAILED
            }
        }
    }

    fun updateNewUnit(transform: (StockActionForm) -> StockActionForm) {
        newUnitDraft.update { it?.copy(form = transform(it.form), failed = false) }
    }

    fun closeNewUnit() {
        newUnitDraft.value = null
    }

    fun saveNewUnit() {
        val draft = newUnitDraft.value ?: return
        val ui = newUnit.value?.ui ?: return
        if (!ui.canSave) return
        newUnitDraft.value = draft.copy(saving = true, failed = false)
        viewModelScope.launch {
            try {
                val unit = PendingUnit(draft.form.serial.trim().ifEmpty { null }, draft.form.itemStatus, draft.form.itemAttributes)
                dao.addPendingUnit(reviewId, draft.article.id, draft.locationId, unit, now(), newId)
                newUnitDraft.value = null
                clearSearch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateSerialException) {
                newUnitDraft.update { it?.copy(saving = false, rejectedSerial = e.serial) }
            } catch (e: ReviewClosedException) {
                newUnitDraft.value = null
                message.value = ReviewMessage.CLOSED
            } catch (e: Exception) {
                newUnitDraft.update { it?.copy(saving = false, failed = true) }
            }
        }
    }

    private fun buildNewUnit(draft: NewUnitDraft, review: ReviewUiState): NewUnitSheetState {
        val pendingSerials = review.lines.values.mapNotNull { it.line.pendingUnit?.serialNumber }
        val context = StockActionContext(
            stockByLocation = emptyMap(),
            activeLocationIds = setOf(draft.locationId),
            // Only the few pending serials are indexed per keystroke, not every serial in the database
            serials = draft.serials + pendingSerials,
            items = emptyList(),
            itemAttributes = draft.itemAttributes,
        )
        val path = review.paths[draft.locationId] ?: ""
        return NewUnitSheetState(
            articleName = draft.article.name,
            unit = draft.article.unit,
            locationPath = path,
            ui = StockActionUiState(
                form = draft.form,
                errors = draft.form.validate(context, draft.rejectedSerial),
                locations = emptyList(),
                sourcePath = path,
                availableMilli = null,
                unitSerial = null,
                itemAttributes = draft.itemAttributes,
                inheritedAttributes = draft.article.attributes,
                saving = draft.saving,
                saveFailed = draft.failed,
            ),
        )
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

    /**
     * A scanned tag jumps to the line of its article (the first unchecked one), all lines shown.
     * A location tag jumps to the group of that location (or of its first sub-location with lines).
     */
    fun onTag(event: TagEvent) {
        when (event) {
            TagEvent.Failed -> message.value = ReviewMessage.TAG_FAILED
            is TagEvent.Read -> viewModelScope.launch {
                val owner = try {
                    findTagOwner(event.tagId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val article = when (owner) {
                    is TagOwner.OfArticle -> owner.article
                    is TagOwner.OfLocation -> {
                        jumpToLocation(owner.location.id)
                        return@launch
                    }
                    null -> {
                        message.value = ReviewMessage.TAG_UNKNOWN
                        return@launch
                    }
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

    private suspend fun jumpToLocation(locationId: String) {
        val current = state.first { !it.loading }
        if (current.scopeLocations.none { it.id == locationId }) {
            message.value = ReviewMessage.TAG_LOCATION_OUTSIDE
            return
        }
        // All lines shown: the location may only have checked lines
        filter.value = LineFilter.ALL
        val all = state.first { !it.loading && it.filter == LineFilter.ALL }
        val group = groupForLocation(locationId, all.scopeLocations, all.groups)
        if (group == null) {
            message.value = ReviewMessage.TAG_LOCATION_EMPTY
            return
        }
        jumpToGroup.value = group
    }

    fun consumeJump() {
        jumpTo.value = null
        jumpToGroup.value = null
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
        val pending = line.pendingUnit
        val attributes = when {
            article == null -> ""
            // Unit values first, then the article's own
            pending != null -> listOf(
                catalog.format(article.copy(attributes = pending.attributes)).summaryLine(),
                catalog.format(article).summaryLine(),
            ).filter { it.isNotEmpty() }.joinToString(" · ")
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
            serialNumber = item?.serialNumber ?: pending?.serialNumber,
            itemStatus = item?.status ?: pending?.status,
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

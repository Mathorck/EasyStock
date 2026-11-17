package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.attributes.AttributeRow
import ch.mathorck.easystock.auth.AppPermissions
import ch.mathorck.easystock.data.db.ArticleDetailDao
import ch.mathorck.easystock.data.db.ArticleFlagDao
import ch.mathorck.easystock.data.db.ArticleFlagWithAuthor
import ch.mathorck.easystock.data.db.ArticleNotFoundException
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.DeletedCategoryNameException
import ch.mathorck.easystock.data.db.DuplicateSkuException
import ch.mathorck.easystock.data.db.InfoFlag
import ch.mathorck.easystock.data.db.LocationStock
import ch.mathorck.easystock.data.db.MovementWithLocation
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.isLowAt
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.images.ArticleImageStore
import ch.mathorck.easystock.images.StagedImages
import ch.mathorck.easystock.util.formatQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ---------- Detail ----------

/** Stock of the article at one location. [path] is e.g. "Workshop › Shelf A". */
data class LocationStockRow(
    val locationId: String,
    val path: String,
    val stockMilli: Long,
    val icon: String? = null,
    val color: String? = null,
)

/** One line of the movement history. [occurredAt] is formatted in the local time zone. */
data class MovementRow(
    val id: String,
    val occurredAt: String,
    val reason: MovementReason,
    val location: String,
    val quantityMilli: Long,
    val comment: String?,
    /** Display name of who made it (server mode). Null in standalone mode or when the author is unknown. */
    val author: String? = null,
)

/**
 * Paths and tree order of [locations], built once and shared by the rows of one emission.
 * [locations] should include soft-deleted ones so every movement location has a path.
 */
class LocationPaths(locations: List<Location>) {
    // buildLocationOptions is already sorted by path (parents before children, ignoring case)
    private val options = buildLocationOptions(locations)
    val order: Map<String, Int> = options.withIndex().associate { (index, option) -> option.id to index }
    val paths: Map<String, String> = options.associate { it.id to it.path }
    val byId: Map<String, Location> = locations.associateBy { it.id }
}

/** Rows for the locations holding a non-zero stock, sorted by path. */
fun locationStockRows(stock: List<LocationStock>, locations: List<Location>): List<LocationStockRow> =
    locationStockRows(stock, LocationPaths(locations))

fun locationStockRows(stock: List<LocationStock>, locations: LocationPaths): List<LocationStockRow> =
    stock
        .filter { it.stockMilli != 0L }
        .sortedBy { locations.order[it.locationId] ?: Int.MAX_VALUE }
        .map {
            val location = locations.byId[it.locationId]
            LocationStockRow(it.locationId, locations.paths[it.locationId] ?: it.locationId, it.stockMilli, location?.icon, location?.color)
        }

/** History rows in the given order (the query sorts newest first). The location is shown with its path. */
fun movementRows(movements: List<MovementWithLocation>, locations: List<Location>, timeZone: TimeZone): List<MovementRow> =
    movementRows(movements, LocationPaths(locations), timeZone)

fun movementRows(movements: List<MovementWithLocation>, locations: LocationPaths, timeZone: TimeZone): List<MovementRow> {
    val paths = locations.paths
    return movements.map { (movement, locationName, authorName) ->
        MovementRow(
            id = movement.id,
            occurredAt = formatDateTime(movement.occurredAt, timeZone),
            reason = movement.reason,
            location = paths[movement.locationId] ?: locationName,
            quantityMilli = movement.quantityMilli,
            comment = movement.comment?.takeIf { it.isNotBlank() },
            author = authorName?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * An open "info to fix" flag of the article, from a finished review. [location] is the path of the line
 * location, [flaggedAt] the review finish time (local time zone), [serialNumber] set for a unit.
 */
data class InfoFlagRow(
    val lineId: String,
    val note: String?,
    val location: String,
    val serialNumber: String?,
    val flaggedAt: String,
)

/** One entry of the article history: a stock movement, or an "info to fix" flag raised or fixed. */
sealed interface HistoryRow {
    /** Unique among the rows, for the list keys. */
    val key: String

    data class Movement(val movement: MovementRow) : HistoryRow {
        override val key: String get() = "movement-${movement.id}"
    }

    /** Flag raised in a review, dated when the review was finished. */
    data class InfoFlagged(val flag: InfoFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "flagged-${flag.lineId}"
    }

    /** Flag marked as fixed. */
    data class InfoFixed(val flag: InfoFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "fixed-${flag.lineId}"
    }

    /** Manual flag ("Report a problem"), dated when it was raised. */
    data class ProblemReported(val flag: ManualFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "problem-reported-${flag.id}"
    }

    /** Manual flag resolved. */
    data class ProblemFixed(val flag: ManualFlagRow, val at: String) : HistoryRow {
        override val key: String get() = "problem-fixed-${flag.id}"
    }
}

/** Open flags (not fixed yet), in the given order (the query sorts the newest review first). */
fun openInfoFlagRows(flags: List<InfoFlag>, locations: List<Location>, timeZone: TimeZone): List<InfoFlagRow> =
    openInfoFlagRows(flags, LocationPaths(locations), timeZone)

fun openInfoFlagRows(flags: List<InfoFlag>, locations: LocationPaths, timeZone: TimeZone): List<InfoFlagRow> =
    flags.filter { it.fixedAt == null }.map { it.toRow(locations.paths, timeZone) }

private fun InfoFlag.toRow(paths: Map<String, String>, timeZone: TimeZone) = InfoFlagRow(
    lineId = lineId,
    note = note?.takeIf { it.isNotBlank() },
    location = paths[locationId] ?: locationName,
    serialNumber = serialNumber?.takeIf { it.isNotBlank() },
    flaggedAt = formatDateTime(flaggedAt, timeZone),
)

/**
 * Movements and flag events merged, newest first. A flag gives a "flagged" entry at the review finish time and,
 * once fixed, a "fixed" entry. At the same time a movement comes before a flag event (the order is stable).
 * Manual flags ([manualFlags], resolved ones included) give a "reported" entry and, once resolved, a "fixed" one.
 * [moreMovements]: [movements] are only the newest ones (see [HISTORY_PAGE_SIZE]); flag events older than the
 * oldest movement shown are left out until more movements are loaded, so the history has no gap.
 */
fun historyRows(
    movements: List<MovementWithLocation>,
    flags: List<InfoFlag>,
    locations: List<Location>,
    timeZone: TimeZone,
    moreMovements: Boolean = false,
    manualFlags: List<ArticleFlagWithAuthor> = emptyList(),
): List<HistoryRow> = historyRows(movements, flags, LocationPaths(locations), timeZone, moreMovements, manualFlags)

fun historyRows(
    movements: List<MovementWithLocation>,
    flags: List<InfoFlag>,
    locations: LocationPaths,
    timeZone: TimeZone,
    moreMovements: Boolean = false,
    manualFlags: List<ArticleFlagWithAuthor> = emptyList(),
): List<HistoryRow> {
    val paths = locations.paths
    val cutoff = if (moreMovements) movements.lastOrNull()?.movement?.occurredAt else null
    val movementEntries = movements.zip(movementRows(movements, locations, timeZone)) { raw, row ->
        raw.movement.occurredAt to HistoryRow.Movement(row)
    }
    val flagEntries = flags.flatMap { flag ->
        val row = flag.toRow(paths, timeZone)
        listOfNotNull(
            flag.fixedAt?.let { it to HistoryRow.InfoFixed(row, formatDateTime(it, timeZone)) },
            flag.flaggedAt to HistoryRow.InfoFlagged(row, row.flaggedAt),
        )
    }
    val manualEntries = manualFlags.flatMap { raw ->
        val row = raw.toRow(timeZone)
        listOfNotNull(
            raw.flag.resolvedAt?.let { it to HistoryRow.ProblemFixed(row, formatDateTime(it, timeZone)) },
            raw.flag.createdAt to HistoryRow.ProblemReported(row, row.createdAt),
        )
    }
    val allFlags = flagEntries + manualEntries
    val shownFlags = if (cutoff == null) allFlags else allFlags.filter { it.first >= cutoff }
    return (movementEntries + shownFlags).sortedByDescending { it.first }.map { it.second }
}

private val DATE_TIME_FORMAT = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
}

/** Formats epoch milliseconds (UTC) as "2026-11-10 14:57" in [timeZone]. */
fun formatDateTime(epochMillis: Long, timeZone: TimeZone): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).format(DATE_TIME_FORMAT)

/** Quantity with an explicit sign: 1500 -> "+1.5", -2000 -> "-2", 0 -> "0". */
fun formatSignedQuantity(milli: Long): String = if (milli > 0) "+${formatQuantity(milli)}" else formatQuantity(milli)

data class ArticleDetailUiState(
    /** True until the database has answered. */
    val loading: Boolean = true,
    /** Null once loaded means the article is deleted (or never existed). */
    val article: Article? = null,
    val categoryName: String? = null,
    val totalMilli: Long = 0,
    val locations: List<LocationStockRow> = emptyList(),
    /** Movements and "info to fix" events, newest first. Only the newest movements, see [hasMoreHistory]. */
    val history: List<HistoryRow> = emptyList(),
    /** Older movements are not loaded yet: the screen offers to show more. */
    val hasMoreHistory: Boolean = false,
    /** Open "info to fix" flags from finished reviews. */
    val infoFlags: List<InfoFlagRow> = emptyList(),
    /** Open manual flags ("Report a problem"). */
    val problems: List<ManualFlagRow> = emptyList(),
    /** Attribute values of the article, empty ones skipped, in the category order. */
    val properties: List<AttributeRow> = emptyList(),
) {
    val isDeleted: Boolean get() = !loading && article == null

    /** Same rule as [ch.mathorck.easystock.data.model.ArticleStock.isLowStock]. */
    val isLowStock: Boolean get() = article?.isLowAt(totalMilli) ?: false
}

/** Movements loaded at first, and added by each "show more". */
const val HISTORY_PAGE_SIZE = 50

/**
 * The newest [limit] movements, read with one extra row ([limit] + 1) to know whether there are more.
 */
data class MovementPage(val movements: List<MovementWithLocation>, val hasMore: Boolean) {
    companion object {
        fun of(rows: List<MovementWithLocation>, limit: Int): MovementPage =
            MovementPage(rows.take(limit), rows.size > limit)
    }
}

/**
 * Detail of one article (stock per location, history) and its edit form.
 * [computeDispatcher] builds the rows off the main thread (tests pass their own).
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class ArticleDetailViewModel(
    private val articleId: String,
    private val dao: ArticleDetailDao,
    categoryDao: CategoryDao,
    attributeDao: AttributeDao,
    flagDao: ArticleFlagDao,
    private val images: ArticleImageStore,
    /** Current permissions, read at each write: the flag writes are refused when the role cannot do them. */
    permissions: () -> AppPermissions,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val newId: () -> String = { Uuid.random().toString() },
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /** Number of movements shown, raised by [showMoreHistory]. */
    private val historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)

    private val movements: Flow<MovementPage> = historyLimit.flatMapLatest { limit ->
        dao.observeMovements(articleId, limit + 1).map { MovementPage.of(it, limit) }
    }

    val state: StateFlow<ArticleDetailUiState> = combine(
        dao.observeArticle(articleId),
        dao.observeStockByLocation(articleId),
        combine(movements, dao.observeInfoFlags(articleId), flagDao.observeForArticle(articleId), ::Triple),
        // Paths built once per location change, shared by all the rows
        dao.observeAllLocations().map(::LocationPaths),
        combine(attributeDao.observeDefinitions(), attributeDao.observeActiveLinks(), ::AttributeCatalog),
    ) { article, stock, (page, flags, problems), locations, catalog ->
        ArticleDetailUiState(
            loading = false,
            article = article?.article,
            categoryName = article?.categoryName,
            totalMilli = stock.sumOf { it.stockMilli },
            locations = locationStockRows(stock, locations),
            history = historyRows(page.movements, flags, locations, timeZone, page.hasMore, problems),
            hasMoreHistory = page.hasMore,
            infoFlags = openInfoFlagRows(flags, locations, timeZone),
            problems = manualFlagRows(problems, timeZone),
            properties = article?.article?.let { catalog.rows(it.categoryId, it.attributes) }.orEmpty(),
        )
    }.flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleDetailUiState())

    /** Loads [HISTORY_PAGE_SIZE] older movements. */
    fun showMoreHistory() {
        historyLimit.update { it + HISTORY_PAGE_SIZE }
    }

    /** Null when the edit form is closed. */
    private val form = MutableStateFlow<ArticleForm?>(null)
    private val status = MutableStateFlow(FormSaveStatus())
    private var latest: ArticleFormUiState? = null
    private var staged = StagedImages()

    /** Bumped when the form opens or closes, so a late save result is ignored. */
    private var generation = 0

    val editState: StateFlow<ArticleFormUiState?> = combine(
        combine(form, formAttributes(form, attributeDao)) { form, attributes -> form to attributes },
        state,
        categoryDao.observeAll(),
        dao.observeSkus().map(::SkuIndex),
        status,
    ) { (form, attributes), detail, categories, skus, status ->
        val article = detail.article
        if (form == null || article == null) {
            null
        } else {
            articleFormState(ArticleFormMode.EDIT, form, categories, attributes, status, detail.categoryName) { shown ->
                shown.validate(article, skus, categories, status.rejectedSku, status.rejectedCategoryName)
            }
        }.also { latest = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opens the form with the stored values. The form is not updated by later changes of the article. */
    fun startEdit() {
        val article = state.value.article ?: return
        if (form.value != null) cancelEdit()
        generation++
        status.value = FormSaveStatus()
        staged = StagedImages(stored = article.imagePath)
        form.value = article.toForm()
    }

    /** A photo was copied for the edit form. Deleted right away if the form is already closed. */
    fun onEditImageImported(path: String) {
        if (form.value == null) {
            deleteImages(images, setOf(path))
            return
        }
        staged += path
        updateEdit { it.copy(imagePath = path) }
    }

    /** Same as [NewArticleViewModel.update]. */
    fun updateEdit(transform: (ArticleForm) -> ArticleForm) {
        val templateName = latest?.templateName
        form.update { it?.withTemplateName(templateName)?.let(transform) }
        status.update { it.copy(failed = false) }
    }

    /**
     * Closes without saving. The photos taken meanwhile are deleted (the stored one stays), unless
     * a save is still running: the save then deletes what it doesn't keep.
     */
    fun cancelEdit() {
        if (!status.value.saving) deleteImages(images, staged.toDeleteOnCancel())
        closeEdit()
    }

    private fun closeEdit() {
        generation++
        form.value = null
        status.value = FormSaveStatus()
        staged = StagedImages()
    }

    /**
     * Sets the photo from the detail screen, saved at once (null removes it).
     * The previous file is deleted once the new path is stored, the new one if saving fails.
     */
    fun setImage(path: String?) {
        viewModelScope.launch {
            try {
                val previous = dao.setImagePath(articleId, path, clock.now().toEpochMilliseconds())
                if (previous != null && previous != path) images.delete(previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Deleted meanwhile, or the write failed: the new file is not used
                if (path != null) images.delete(path)
            }
        }
    }

    /** Marks an open "info to fix" flag as fixed: it leaves the card and a "fixed" entry joins the history. */
    fun markInfoFixed(lineId: String) {
        viewModelScope.launch {
            try {
                dao.markInfoFixed(lineId, clock.now().toEpochMilliseconds())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The write failed: the flag stays open and can be marked again
            }
        }
    }

    private val flagActions = ArticleFlagActions(flagDao.asStore(), permissions, clock, newId)

    /** Raises a manual flag on the article. Refused (nothing written) when the role cannot flag or the note is invalid. */
    fun reportProblem(note: String) {
        viewModelScope.launch {
            try {
                flagActions.report(articleId, note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The write failed: nothing was added, the user can report again
            }
        }
    }

    /** Resolves a manual flag. Refused (nothing written) when the role cannot edit, so a Viewer never queues it. */
    fun markProblemFixed(flagId: String) {
        viewModelScope.launch {
            try {
                flagActions.resolve(flagId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The write failed: the flag stays open and can be marked again
            }
        }
    }

    /** Saves the form. The form closes on success. */
    fun save() {
        val current = latest ?: return
        if (!current.canSave || status.value.saving || current.form != form.value?.withTemplateName(current.templateName)) return
        status.update { it.copy(saving = true, failed = false) }
        val startedIn = generation
        val session = staged
        viewModelScope.launch {
            var saved = false
            try {
                val now = clock.now().toEpochMilliseconds()
                val update = current.form.toUpdate(current.categories, now, newId, current.followsTemplate)
                val previous = dao.updateArticle(articleId, update.changes, update.newCategory, now)
                saved = true
                // Same form: photos may have been added meanwhile, they are in the current set
                val photos = if (startedIn == generation) staged else session
                deleteImages(images, photos.toDeleteOnSave(update.changes.imagePath, previous))
                if (startedIn == generation) closeEdit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateSkuException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedSku = e.sku) }
            } catch (e: DeletedCategoryNameException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedCategoryName = e.name) }
            } catch (e: ArticleNotFoundException) {
                // Deleted meanwhile: the detail screen goes back to the list
                if (startedIn == generation) cancelEdit()
            } catch (e: Exception) {
                if (startedIn == generation) status.update { it.copy(saving = false, failed = true) }
            } finally {
                // Closed while saving and nothing saved: the photos of that form are unused
                if (!saved && startedIn != generation) deleteImages(images, session.toDeleteOnCancel())
            }
        }
    }
}

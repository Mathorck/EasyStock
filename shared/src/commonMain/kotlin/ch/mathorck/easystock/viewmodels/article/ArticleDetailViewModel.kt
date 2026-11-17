package ch.mathorck.easystock.viewmodels.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dao.alerts.DateAlertDao
import ch.mathorck.easystock.data.dao.article.ArticleDetailDao
import ch.mathorck.easystock.data.dao.article.ArticleFlagDao
import ch.mathorck.easystock.data.dao.article.ArticleFlagWithAuthor
import ch.mathorck.easystock.data.dao.article.InfoFlag
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.categories.CategoryDao
import ch.mathorck.easystock.data.dao.location.LocationStock
import ch.mathorck.easystock.data.dao.stock.MovementWithLocation
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.article.ArticleFormMode
import ch.mathorck.easystock.exceptions.article.ArticleNotFoundException
import ch.mathorck.easystock.exceptions.article.DuplicateSkuException
import ch.mathorck.easystock.exceptions.categories.DeletedCategoryNameException
import ch.mathorck.easystock.services.attributes.AttributeCatalog
import ch.mathorck.easystock.services.auth.AppPermissions
import ch.mathorck.easystock.services.images.ArticleImageStore
import ch.mathorck.easystock.services.images.StagedImages
import ch.mathorck.easystock.util.formatQuantity
import ch.mathorck.easystock.viewmodels.ArticleFlagActions
import ch.mathorck.easystock.viewmodels.MovementPage
import ch.mathorck.easystock.viewmodels.alerts.observePastDates
import ch.mathorck.easystock.viewmodels.alerts.pastDates
import ch.mathorck.easystock.viewmodels.alerts.todayFlow
import ch.mathorck.easystock.viewmodels.location.LocationPaths
import ch.mathorck.easystock.viewmodels.location.LocationStockRow
import ch.mathorck.easystock.viewmodels.stock.SkuIndex
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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

/** Movements loaded at first, and added by each "show more". */
const val HISTORY_PAGE_SIZE = 50

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
    dateAlertDao: DateAlertDao,
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
        combine(
            combine(attributeDao.observeDefinitions(), attributeDao.observeActiveLinks(), ::AttributeCatalog),
            // Only this article and its units are read, and nothing when no definition flags
            observePastDates(
                attributeDao.observeDefinitions(),
                { dateAlertDao.observeArticle(articleId) },
                { dateAlertDao.observeItemsOf(articleId) },
                todayFlow(),
            ),
            ::Pair,
        ),
    ) { article, stock, (page, flags, problems), locations, (catalog, past) ->
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
            pastDates = past,
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

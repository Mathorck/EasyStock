package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.ArticleDetailDao
import ch.mathorck.easystock.data.db.ArticleNotFoundException
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.DeletedCategoryNameException
import ch.mathorck.easystock.data.db.DuplicateSkuException
import ch.mathorck.easystock.data.db.LocationStock
import ch.mathorck.easystock.data.db.MovementWithLocation
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.util.formatQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
data class LocationStockRow(val locationId: String, val path: String, val stockMilli: Long)

/** One line of the movement history. [occurredAt] is formatted in the local time zone. */
data class MovementRow(
    val id: String,
    val occurredAt: String,
    val reason: MovementReason,
    val location: String,
    val quantityMilli: Long,
    val comment: String?,
)

/**
 * Rows for the locations holding a non-zero stock, sorted by path.
 * [locations] should include soft-deleted ones so every movement location has a path.
 */
fun locationStockRows(stock: List<LocationStock>, locations: List<Location>): List<LocationStockRow> {
    // buildLocationOptions is already sorted by path (parents before children, ignoring case)
    val options = buildLocationOptions(locations)
    val order = options.withIndex().associate { (index, option) -> option.id to index }
    val paths = options.associate { it.id to it.path }
    return stock
        .filter { it.stockMilli != 0L }
        .sortedBy { order[it.locationId] ?: Int.MAX_VALUE }
        .map { LocationStockRow(it.locationId, paths[it.locationId] ?: it.locationId, it.stockMilli) }
}

/** History rows in the given order (the query sorts newest first). The location is shown with its path. */
fun movementRows(movements: List<MovementWithLocation>, locations: List<Location>, timeZone: TimeZone): List<MovementRow> {
    val paths = buildLocationOptions(locations).associate { it.id to it.path }
    return movements.map { (movement, locationName) ->
        MovementRow(
            id = movement.id,
            occurredAt = formatDateTime(movement.occurredAt, timeZone),
            reason = movement.reason,
            location = paths[movement.locationId] ?: locationName,
            quantityMilli = movement.quantityMilli,
            comment = movement.comment?.takeIf { it.isNotBlank() },
        )
    }
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
    val movements: List<MovementRow> = emptyList(),
) {
    val isDeleted: Boolean get() = !loading && article == null

    /** Same rule as [ch.mathorck.easystock.data.model.ArticleStock.isLowStock]. */
    val isLowStock: Boolean get() = article?.thresholdMilli?.let { totalMilli <= it } ?: false
}

/** Detail of one article (stock per location, history) and its edit form. */
@OptIn(ExperimentalUuidApi::class)
class ArticleDetailViewModel(
    private val articleId: String,
    private val dao: ArticleDetailDao,
    categoryDao: CategoryDao,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    val state: StateFlow<ArticleDetailUiState> = combine(
        dao.observeArticle(articleId),
        dao.observeStockByLocation(articleId),
        dao.observeMovements(articleId),
        dao.observeAllLocations(),
    ) { article, stock, movements, locations ->
        ArticleDetailUiState(
            loading = false,
            article = article?.article,
            categoryName = article?.categoryName,
            totalMilli = stock.sumOf { it.stockMilli },
            locations = locationStockRows(stock, locations),
            movements = movementRows(movements, locations, timeZone),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleDetailUiState())

    /** Null when the edit form is closed. */
    private val form = MutableStateFlow<ArticleForm?>(null)
    private val status = MutableStateFlow(FormSaveStatus())
    private var latest: ArticleFormUiState? = null

    /** Bumped when the form opens or closes, so a late save result is ignored. */
    private var generation = 0

    val editState: StateFlow<ArticleFormUiState?> = combine(
        form,
        state,
        categoryDao.observeAll(),
        dao.observeSkus(),
        status,
    ) { form, detail, categories, skus, status ->
        val article = detail.article
        if (form == null || article == null) {
            null
        } else {
            ArticleFormUiState(
                mode = ArticleFormMode.EDIT,
                form = form,
                categories = categories,
                currentCategoryName = detail.categoryName,
                errors = form.validate(article, skus, categories, status.rejectedSku, status.rejectedCategoryName),
                saving = status.saving,
                saveFailed = status.failed,
            )
        }.also { latest = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opens the form with the stored values. The form is not updated by later changes of the article. */
    fun startEdit() {
        val article = state.value.article ?: return
        generation++
        status.value = FormSaveStatus()
        form.value = article.toForm()
    }

    fun updateEdit(transform: (ArticleForm) -> ArticleForm) {
        form.update { it?.let(transform) }
        status.update { it.copy(failed = false) }
    }

    fun cancelEdit() {
        generation++
        form.value = null
        status.value = FormSaveStatus()
    }

    /** Saves the form. The form closes on success. */
    fun save() {
        val current = latest ?: return
        if (!current.canSave || status.value.saving || current.form != form.value) return
        status.update { it.copy(saving = true, failed = false) }
        val startedIn = generation
        viewModelScope.launch {
            try {
                val now = clock.now().toEpochMilliseconds()
                val update = current.form.toUpdate(current.categories, now, newId)
                dao.updateArticle(articleId, update.changes, update.newCategory, now)
                if (startedIn == generation) cancelEdit()
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
            }
        }
    }
}

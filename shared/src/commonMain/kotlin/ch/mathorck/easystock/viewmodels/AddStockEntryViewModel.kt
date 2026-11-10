package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.ArticleDao
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.DeletedCategoryNameException
import ch.mathorck.easystock.data.db.DuplicateSkuException
import ch.mathorck.easystock.data.db.NewStockEntry
import ch.mathorck.easystock.data.db.STOCK_ENTRY_REASONS
import ch.mathorck.easystock.data.db.StockDao
import ch.mathorck.easystock.data.db.StockEntryDao
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.util.parseQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Max lengths from docs/schema.dbml
const val MAX_NAME_LENGTH = 255
const val MAX_SKU_LENGTH = 50
const val MAX_UNIT_LENGTH = 20
const val MAX_CATEGORY_NAME_LENGTH = 100
const val MAX_LOCATION_NAME_LENGTH = 100

const val DEFAULT_UNIT = "pce"

/** Pick an existing row or create a new one. */
enum class ChoiceMode { EXISTING, NEW }

enum class CategoryMode { NONE, EXISTING, NEW }

/** Raw form input, as typed. */
data class AddStockEntryForm(
    val articleMode: ChoiceMode = ChoiceMode.EXISTING,
    val articleQuery: String = "",
    val articleId: String? = null,
    val name: String = "",
    val sku: String = "",
    val unit: String = DEFAULT_UNIT,
    val categoryMode: CategoryMode = CategoryMode.NONE,
    val categoryId: String? = null,
    val categoryName: String = "",
    val threshold: String = "",
    val locationMode: ChoiceMode = ChoiceMode.EXISTING,
    val locationId: String? = null,
    val locationName: String = "",
    val locationParentId: String? = null,
    val quantity: String = "",
    val reason: MovementReason = MovementReason.PURCHASE,
    val comment: String = "",
)

enum class FieldError { REQUIRED, TOO_LONG, DUPLICATE, DELETED_NAME, INVALID_QUANTITY, NOT_POSITIVE }

/** One error per field, null when the field is valid. */
data class FormErrors(
    val article: FieldError? = null,
    val name: FieldError? = null,
    val sku: FieldError? = null,
    val unit: FieldError? = null,
    val category: FieldError? = null,
    val categoryName: FieldError? = null,
    val threshold: FieldError? = null,
    val location: FieldError? = null,
    val locationName: FieldError? = null,
    val quantity: FieldError? = null,
) {
    val isValid: Boolean
        get() = listOf(article, name, sku, unit, category, categoryName, threshold, location, locationName, quantity).all { it == null }
}

/** A location with its full path, e.g. "Room › Shelf". [depth] is 0 for top level. */
data class LocationOption(val id: String, val name: String, val path: String, val depth: Int)

/** Articles that can receive a stock entry: quantity-tracked, not archived. */
fun List<Article>.entryCandidates(): List<Article> = filter { it.tracking == Tracking.QUANTITY && !it.archived }

/** Keeps the articles whose name or SKU contains [query], ignoring case. A blank query keeps all. */
fun List<Article>.matching(query: String): List<Article> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.sku?.contains(q, ignoreCase = true) == true }
}

private const val MAX_LOCATION_DEPTH = 16

/**
 * Builds the location paths ("Room › Shelf › Bin"), sorted as a tree: each parent is followed by its children.
 * A missing parent (deleted) makes the location top level. A cycle or a very deep chain is cut.
 */
fun buildLocationOptions(locations: List<Location>): List<LocationOption> {
    val byId = locations.associateBy { it.id }
    fun segments(location: Location): List<String> {
        val names = mutableListOf(location.name)
        val seen = mutableSetOf(location.id)
        var parentId = location.parentId
        while (parentId != null && names.size < MAX_LOCATION_DEPTH) {
            val parent = byId[parentId] ?: break
            if (!seen.add(parent.id)) break
            names.add(parent.name)
            parentId = parent.parentId
        }
        return names.asReversed()
    }
    val segmentComparator = Comparator<List<String>> { a, b ->
        for (i in 0 until minOf(a.size, b.size)) {
            val c = a[i].compareTo(b[i], ignoreCase = true)
            if (c != 0) return@Comparator c
        }
        a.size.compareTo(b.size)
    }
    return locations
        .map { it to segments(it) }
        .sortedWith(compareBy(segmentComparator) { it.second })
        .map { (location, path) ->
            LocationOption(location.id, location.name, path.joinToString(" › "), path.size - 1)
        }
}

/**
 * Checks the form against the current data.
 * [rejectedSku] / [rejectedCategoryName]: values the database refused on the last save.
 */
fun AddStockEntryForm.validate(
    articles: List<Article>,
    categories: List<Category>,
    locations: List<Location>,
    rejectedSku: String? = null,
    rejectedCategoryName: String? = null,
): FormErrors {
    val newArticle = articleMode == ChoiceMode.NEW
    val newLocation = locationMode == ChoiceMode.NEW
    val skuValue = sku.trim()
    val categoryValue = categoryName.trim()
    val quantityMilli = parseQuantity(quantity)
    return FormErrors(
        article = if (!newArticle && articles.entryCandidates().none { it.id == articleId }) FieldError.REQUIRED else null,
        name = if (newArticle) text(name, MAX_NAME_LENGTH) else null,
        sku = when {
            !newArticle || skuValue.isEmpty() -> null
            skuValue.length > MAX_SKU_LENGTH -> FieldError.TOO_LONG
            articles.any { it.sku.equals(skuValue, ignoreCase = true) } -> FieldError.DUPLICATE
            rejectedSku != null && rejectedSku.equals(skuValue, ignoreCase = true) -> FieldError.DUPLICATE
            else -> null
        },
        unit = if (newArticle) text(unit, MAX_UNIT_LENGTH) else null,
        category = if (newArticle && categoryMode == CategoryMode.EXISTING && categories.none { it.id == categoryId }) {
            FieldError.REQUIRED
        } else {
            null
        },
        categoryName = when {
            !newArticle || categoryMode != CategoryMode.NEW -> null
            text(categoryName, MAX_CATEGORY_NAME_LENGTH) != null -> text(categoryName, MAX_CATEGORY_NAME_LENGTH)
            rejectedCategoryName != null && rejectedCategoryName.equals(categoryValue, ignoreCase = true) ->
                FieldError.DELETED_NAME
            else -> null
        },
        threshold = if (newArticle && threshold.isNotBlank() && parseQuantity(threshold) == null) {
            FieldError.INVALID_QUANTITY
        } else {
            null
        },
        location = if (!newLocation && locations.none { it.id == locationId }) FieldError.REQUIRED else null,
        locationName = if (newLocation) text(locationName, MAX_LOCATION_NAME_LENGTH) else null,
        quantity = when {
            quantity.isBlank() -> FieldError.REQUIRED
            quantityMilli == null -> FieldError.INVALID_QUANTITY
            quantityMilli <= 0 -> FieldError.NOT_POSITIVE
            else -> null
        },
    )
}

private fun text(value: String, maxLength: Int): FieldError? = when {
    value.isBlank() -> FieldError.REQUIRED
    value.trim().length > maxLength -> FieldError.TOO_LONG
    else -> null
}

/**
 * Builds the rows to save. Call only on a valid form.
 * [categories]: active categories, a new category with the same name (ignoring case) reuses it.
 */
fun AddStockEntryForm.toEntry(categories: List<Category>, now: Long, newId: () -> String): NewStockEntry {
    val quantityMilli = requireNotNull(parseQuantity(quantity)) { "Invalid quantity" }
    require(quantityMilli > 0) { "Quantity must be positive" }
    require(reason in STOCK_ENTRY_REASONS) { "Not an entry reason: $reason" }

    var newCategory: Category? = null
    val article = if (articleMode == ChoiceMode.NEW) {
        val categoryId = when (categoryMode) {
            CategoryMode.NONE -> null
            CategoryMode.EXISTING -> categoryId
            CategoryMode.NEW -> {
                val wanted = categoryName.trim()
                categories.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.id
                    ?: Category(id = newId(), name = wanted, createdAt = now, updatedAt = now)
                        .also { newCategory = it }.id
            }
        }
        Article(
            id = newId(),
            sku = sku.trim().ifEmpty { null },
            name = name.trim(),
            categoryId = categoryId,
            tracking = Tracking.QUANTITY,
            unit = unit.trim(),
            thresholdMilli = threshold.takeIf { it.isNotBlank() }?.let { requireNotNull(parseQuantity(it)) },
            createdAt = now,
            updatedAt = now,
        )
    } else {
        null
    }
    val location = if (locationMode == ChoiceMode.NEW) {
        Location(id = newId(), name = locationName.trim(), parentId = locationParentId, createdAt = now, updatedAt = now)
    } else {
        null
    }
    val movement = StockMovement(
        id = newId(),
        articleId = article?.id ?: requireNotNull(articleId) { "No article" },
        locationId = location?.id ?: requireNotNull(locationId) { "No location" },
        quantityMilli = quantityMilli,
        reason = reason,
        comment = comment.trim().ifEmpty { null },
        occurredAt = now,
        createdAt = now,
    )
    return NewStockEntry(newCategory = newCategory, newArticle = article, newLocation = location, movement = movement)
}

data class AddStockEntryUiState(
    /** True until the database has answered, to avoid flashing the empty hints. */
    val loading: Boolean = true,
    val form: AddStockEntryForm = AddStockEntryForm(),
    /** Articles that can receive an entry (quantity-tracked, not archived), all of them. */
    val articles: List<Article> = emptyList(),
    val categories: List<Category> = emptyList(),
    val locations: List<LocationOption> = emptyList(),
    val errors: FormErrors = FormErrors(),
    val saving: Boolean = false,
    /** The last save failed for an unexpected reason. */
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = errors.isValid && !saving
}

private data class SaveStatus(
    val saving: Boolean = false,
    val failed: Boolean = false,
    val rejectedSku: String? = null,
    val rejectedCategoryName: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class AddStockEntryViewModel(
    articleDao: ArticleDao,
    categoryDao: CategoryDao,
    stockDao: StockDao,
    private val entryDao: StockEntryDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private val form = MutableStateFlow(AddStockEntryForm())
    private val status = MutableStateFlow(SaveStatus())
    private var latest = AddStockEntryUiState()

    /** Bumped by [reset], so a save that ends after the sheet was closed (and maybe reopened) is ignored. */
    private var generation = 0

    val state: StateFlow<AddStockEntryUiState> = combine(
        articleDao.observeAll(),
        categoryDao.observeAll(),
        stockDao.observeLocations(),
        form,
        status,
    ) { articles, categories, locations, form, status ->
        AddStockEntryUiState(
            loading = false,
            form = form,
            articles = articles.entryCandidates(),
            categories = categories,
            locations = buildLocationOptions(locations),
            errors = form.validate(articles, categories, locations, status.rejectedSku, status.rejectedCategoryName),
            saving = status.saving,
            saveFailed = status.failed,
        ).also { latest = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddStockEntryUiState())

    fun update(transform: (AddStockEntryForm) -> AddStockEntryForm) {
        form.update(transform)
        status.update { it.copy(failed = false) }
    }

    /** Clears the form, e.g. when the sheet closes (the ViewModel can outlive it). */
    fun reset() {
        generation++
        form.value = AddStockEntryForm()
        status.value = SaveStatus()
    }

    /** Saves the entry. [onSaved] runs only on success, the form is reset first. */
    fun save(onSaved: () -> Unit) {
        val current = latest
        if (!current.canSave || status.value.saving || current.form != form.value) return
        status.update { it.copy(saving = true, failed = false) }
        val startedIn = generation
        viewModelScope.launch {
            try {
                val entry = current.form.toEntry(current.categories, clock.now().toEpochMilliseconds(), newId)
                entryDao.save(entry)
                if (startedIn == generation) {
                    reset()
                    onSaved()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateSkuException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedSku = e.sku) }
            } catch (e: DeletedCategoryNameException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedCategoryName = e.name) }
            } catch (e: Exception) {
                if (startedIn == generation) status.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}

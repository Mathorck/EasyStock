package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.ArticleDao
import ch.mathorck.easystock.data.db.CategoryDao
import ch.mathorck.easystock.data.db.StockDao
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Filters of the Stock page, combined with the search query (AND). */
data class StockFilters(
    /** Stock at this location and its descendants. Null = all locations. */
    val locationId: String? = null,
    val categoryId: String? = null,
    val lowStockOnly: Boolean = false,
) {
    val isActive: Boolean get() = locationId != null || categoryId != null || lowStockOnly
}

/** A location with its depth in the hierarchy (0 = root), for an indented picker. */
data class StockLocationNode(val location: Location, val depth: Int)

data class StockUiState(
    val loading: Boolean = true,
    /** Articles matching the search query and the filters. */
    val items: List<ArticleStock> = emptyList(),
    /** Number of articles before filtering, to tell "no articles" from "no results". */
    val totalCount: Int = 0,
    /** Locations in hierarchy order (depth-first, siblings by name). */
    val locations: List<StockLocationNode> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Active filters. Ids that no longer exist are already dropped. */
    val filters: StockFilters = StockFilters(),
) {
    val selectedLocation: Location? get() = locations.firstOrNull { it.location.id == filters.locationId }?.location
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == filters.categoryId }
}

/** Keeps the articles whose name or SKU contains [query], ignoring case. A blank query keeps all. */
fun List<ArticleStock>.filterByQuery(query: String): List<ArticleStock> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.article.name.contains(q, ignoreCase = true) || it.article.sku?.contains(q, ignoreCase = true) == true }
}

/**
 * Applies the search [query], the category and the low-stock filter (AND).
 * The location filter is applied by the SQL query, not here.
 */
fun List<ArticleStock>.filterStock(query: String, categoryId: String?, lowStockOnly: Boolean): List<ArticleStock> =
    filterByQuery(query).filter { item ->
        (categoryId == null || item.article.categoryId == categoryId) && (!lowStockOnly || item.isLowStock)
    }

/** Drops a location or category id that is not in the known ids (e.g. soft-deleted meanwhile). */
fun StockFilters.withoutStale(locationIds: Set<String>, categoryIds: Set<String>): StockFilters = copy(
    locationId = locationId?.takeIf { it in locationIds },
    categoryId = categoryId?.takeIf { it in categoryIds },
)

/**
 * Flattens the locations into depth-first order with their depth. Siblings keep the input order.
 * A location whose parent is missing (e.g. soft-deleted) is shown as a root. Cycles are cut.
 */
fun List<Location>.toLocationTree(): List<StockLocationNode> {
    val ids = mapTo(HashSet()) { it.id }
    val children = groupBy { it.parentId?.takeIf { parent -> parent in ids } }
    val result = ArrayList<StockLocationNode>(size)
    val visited = HashSet<String>()

    fun visit(location: Location, depth: Int) {
        if (!visited.add(location.id)) return
        result += StockLocationNode(location, depth)
        children[location.id].orEmpty().forEach { visit(it, depth + 1) }
    }

    children[null].orEmpty().forEach { visit(it, 0) }
    // Locations only reachable through a cycle: show them as roots.
    forEach { if (it.id !in visited) visit(it, 0) }
    return result
}

@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModel(articleDao: ArticleDao, stockDao: StockDao, categoryDao: CategoryDao) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(StockFilters())

    private val locations = stockDao.observeLocations().map { it.toLocationTree() }
    private val categories = categoryDao.observeAll()

    private val effectiveFilters: Flow<StockFilters> = combine(_filters, locations, categories) { filters, locs, cats ->
        filters.withoutStale(locs.mapTo(HashSet()) { it.location.id }, cats.mapTo(HashSet()) { it.id })
    }.distinctUntilChanged()

    /**
     * Stock at the selected location, or the total stock without a location filter,
     * tagged with the location id it was queried for.
     */
    private val source: Flow<Pair<String?, List<ArticleStock>>> = effectiveFilters
        .map { it.locationId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            (if (id == null) articleDao.observeStock() else articleDao.observeStockAt(id)).map { id to it }
        }

    val state: StateFlow<StockUiState> = combine(
        articleDao.observeStock(),
        source,
        _query,
        effectiveFilters,
        combine(locations, categories, ::Pair),
    ) { all, (sourceLocationId, items), q, filters, (locs, cats) ->
        // Right after a location change, the list can still be the previous location's: never show it.
        val upToDate = sourceLocationId == filters.locationId
        StockUiState(
            loading = !upToDate,
            items = if (upToDate) items.filterStock(q, filters.categoryId, filters.lowStockOnly) else emptyList(),
            totalCount = all.size,
            locations = locs,
            categories = cats,
            filters = filters,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onLocationSelect(locationId: String?) = _filters.update { it.copy(locationId = locationId) }

    fun onCategorySelect(categoryId: String?) = _filters.update { it.copy(categoryId = categoryId) }

    fun onLowStockChange(enabled: Boolean) = _filters.update { it.copy(lowStockOnly = enabled) }

    fun onClearFilters() {
        _filters.value = StockFilters()
    }
}

package ch.mathorck.easystock.viewmodels.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.formatAttributeValue
import ch.mathorck.easystock.composables.attributes.matches
import ch.mathorck.easystock.composables.attributes.summaryLine
import ch.mathorck.easystock.data.dao.alerts.DateAlertDao
import ch.mathorck.easystock.data.dao.article.ArticleDao
import ch.mathorck.easystock.data.dao.article.ArticleFlagDao
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.categories.CategoryDao
import ch.mathorck.easystock.data.dao.stock.StockDao
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.ArticleStock
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.stock.StockLevelFilter
import ch.mathorck.easystock.enums.tags.TagFilter
import ch.mathorck.easystock.services.attributes.AttributeCatalog
import ch.mathorck.easystock.services.attributes.AttributeText
import ch.mathorck.easystock.viewmodels.alerts.observePastDates
import ch.mathorck.easystock.viewmodels.alerts.todayFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/**
 * True when [article] has a value for [definition], as shown on the article: blank text and false booleans
 * count as no value.
 */
fun Article.hasAttribute(definition: AttributeDefinition): Boolean =
    formatAttributeValue(definition, attributes?.get(definition.key)) != null

/**
 * True when [item] passes the filters other than the location (applied by the SQL query).
 * [attribute] is the definition of [StockFilters.attributeId], null for no attribute filter.
 */
fun StockFilters.matches(item: ArticleStock, attribute: AttributeDefinition? = null): Boolean {
    val article = item.article
    return (categoryId == null || article.categoryId == categoryId) &&
        (tracking == null || article.tracking == tracking) &&
        when (stockLevel) {
            null -> true
            StockLevelFilter.LOW -> item.isLowStock
            StockLevelFilter.OUT -> item.stockMilli <= 0
        } &&
        when (tag) {
            null -> true
            TagFilter.TAGGED -> article.barcode != null
            TagFilter.UNTAGGED -> article.barcode == null
        } &&
        (attribute == null || article.hasAttribute(attribute))
}

/**
 * Keeps the articles whose name, SKU or attribute value contains [query], ignoring case.
 * Attribute values match raw ("50") or with their unit ("50 m"). A blank query keeps all.
 * [attributes] gives the formatted attributes of an article.
 */
fun List<ArticleStock>.filterByQuery(
    query: String,
    attributes: (ArticleStock) -> List<AttributeText> = { emptyList() },
): List<ArticleStock> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter {
        it.article.name.contains(q, ignoreCase = true) ||
            it.article.sku?.contains(q, ignoreCase = true) == true ||
            attributes(it).matches(q)
    }
}

/**
 * Applies the search [query] and the [filters] (AND). The location filter is applied by the SQL query, not here.
 * [attribute] is the definition of [StockFilters.attributeId] (see [matches]).
 */
fun List<ArticleStock>.filterStock(
    query: String,
    filters: StockFilters,
    attribute: AttributeDefinition? = null,
    attributes: (ArticleStock) -> List<AttributeText> = { emptyList() },
): List<ArticleStock> =
    filterByQuery(query, attributes).filter { filters.matches(it, attribute) }

/** Drops a location, category or attribute id that is not in the known ids (e.g. soft-deleted meanwhile). */
fun StockFilters.withoutStale(
    locationIds: Set<String>,
    categoryIds: Set<String>,
    attributeIds: Set<String> = emptySet(),
): StockFilters = copy(
    locationId = locationId?.takeIf { it in locationIds },
    categoryId = categoryId?.takeIf { it in categoryIds },
    attributeId = attributeId?.takeIf { it in attributeIds },
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

/**
 * [computeDispatcher] runs the formatting and filtering off the main thread (tests pass their own).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModel(
    articleDao: ArticleDao,
    stockDao: StockDao,
    categoryDao: CategoryDao,
    attributeDao: AttributeDao,
    flagDao: ArticleFlagDao,
    dateAlertDao: DateAlertDao,
    today: Flow<LocalDate> = todayFlow(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(StockFilters())

    private val locations = stockDao.observeLocations().map { it.toLocationTree() }
    private val categories = categoryDao.observeAll()
    private val definitions = attributeDao.observeDefinitions()
    private val attributeCatalog = combine(definitions, attributeDao.observeActiveLinks(), ::AttributeCatalog)

    private val effectiveFilters: Flow<StockFilters> = combine(_filters, locations, categories, definitions) { filters, locs, cats, defs ->
        filters.withoutStale(
            locs.mapTo(HashSet()) { it.location.id },
            cats.mapTo(HashSet()) { it.id },
            defs.mapTo(HashSet()) { it.id },
        )
    }.distinctUntilChanged()

    /**
     * Stock at the selected location, or the total stock without a location filter,
     * tagged with the location id it was queried for, with the attributes formatted.
     */
    private val source: Flow<FormattedStock> = combine(
        effectiveFilters
            .map { it.locationId }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                (if (id == null) articleDao.observeStock() else articleDao.observeStockAt(id)).map { id to it }
            },
        attributeCatalog,
    ) { (id, items), catalog ->
        val formatted = items.associate { it.article.id to catalog.format(it.article) }
        val lines = formatted.mapNotNull { (articleId, texts) -> texts.takeIf { it.isNotEmpty() }?.let { articleId to it.summaryLine() } }.toMap()
        FormattedStock(id, items, formatted, lines)
    }.flowOn(computeDispatcher)

    val state: StateFlow<StockUiState> = combine(
        // A cheap COUNT: the stock sums are only read for the shown location
        articleDao.observeActiveCount(),
        source,
        _query,
        effectiveFilters,
        combine(
            locations,
            categories,
            definitions,
            flagDao.observeFlaggedArticleIds().map { it.toSet() },
            observePastDates(definitions, dateAlertDao::observeArticles, dateAlertDao::observeItems, today)
                .map { past -> past.groupBy { it.articleId } },
            ::StockLookups,
        ),
    ) { total, stock, q, filters, (locs, cats, defs, flagged, past) ->
        // Right after a location change, the list can still be the previous location's: never show it.
        val upToDate = stock.locationId == filters.locationId
        val attributes = { item: ArticleStock -> stock.attributes[item.article.id].orEmpty() }
        StockUiState(
            loading = !upToDate,
            items = if (upToDate) {
                stock.items.filterStock(q, filters, defs.firstOrNull { it.id == filters.attributeId }, attributes)
            } else {
                emptyList()
            },
            totalCount = total,
            locations = locs,
            categories = cats,
            attributeDefinitions = defs,
            filters = filters,
            attributeLines = if (upToDate) stock.attributeLines else emptyMap(),
            flaggedIds = flagged,
            pastDatesByArticle = past,
        )
    }.flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    /** Replaces the filters. They live in this ViewModel, so they survive switching pages. */
    fun onFiltersChange(filters: StockFilters) {
        _filters.value = filters
    }

    fun onClearFilters() {
        _filters.value = StockFilters()
    }
}

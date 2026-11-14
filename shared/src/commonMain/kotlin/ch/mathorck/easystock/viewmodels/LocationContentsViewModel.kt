package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.attributes.AttributeText
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.LocationArticleStock
import ch.mathorck.easystock.data.db.LocationDao
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One article at one location: its stock there, its attributes and, for serial articles, the units there. */
data class ContentsArticleRow(
    val article: Article,
    val locationId: String,
    val stockMilli: Long,
    val attributes: List<AttributeText>,
    val units: List<Item>,
) {
    val isSerial: Boolean get() = article.tracking == Tracking.SERIAL
}

/** The articles directly at [location]. [path] is relative to the location shown ("" for itself). */
data class ContentsGroup(
    val location: Location,
    val path: String,
    val rows: List<ContentsArticleRow>,
)

/** A direct sub-location, with the number of articles in it and below. */
data class ContentsSubLocation(val location: Location, val articleCount: Int)

data class LocationContentsUiState(
    val loading: Boolean = true,
    /** The location shown, null when it is deleted (or never existed). */
    val location: Location? = null,
    /** Full path of [location], "Room › Shelf". */
    val path: String = "",
    val includeSubLocations: Boolean = true,
    /** The location first, then its descendants in tree order. Only groups with articles. */
    val groups: List<ContentsGroup> = emptyList(),
    val subLocations: List<ContentsSubLocation> = emptyList(),
    /** Can go back to the parent location opened before. */
    val canGoBack: Boolean = false,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val articleCount: Int get() = groups.sumOf { group -> group.rows.size }
}

/**
 * What [rootId] holds: its articles and those of its descendants ([includeSubLocations]), grouped by
 * location in tree order. Only articles with a non-zero stock or a unit there are listed.
 * [locations]: the active locations; [stock] and [items] may cover more locations than the subtree.
 */
fun buildLocationContents(
    rootId: String,
    locations: List<Location>,
    stock: List<LocationArticleStock>,
    items: List<Item>,
    catalog: AttributeCatalog,
    includeSubLocations: Boolean,
): LocationContentsUiState {
    val root = locations.firstOrNull { it.id == rootId }
        ?: return LocationContentsUiState(loading = false, includeSubLocations = includeSubLocations)
    val options = buildLocationOptions(locations).associateBy { it.id }
    val rootPath = options[rootId]?.path ?: root.name
    val children = locations.groupBy { it.parentId }
    val subtree = subtreeIds(rootId, children).toHashSet()
    val byId = locations.associateBy { it.id }
    // Tree order of the subtree: the depth-first order of the paths
    val ordered = locations.toLocationTree().map { it.location.id }.filter { it in subtree }
    val shown = if (includeSubLocations) ordered else listOf(rootId)
    val shownSet = shown.toSet()

    val stockByKey = stock.filter { it.locationId in shownSet }.associateBy { it.locationId to it.article.id }
    val unitsByKey = items.filter { it.locationId in shownSet }.groupBy { it.locationId!! to it.articleId }
    val articles = stock.associate { it.article.id to it.article }

    val keysByLocation = (stockByKey.keys + unitsByKey.keys).groupBy { it.first }
    val groups = shown.mapNotNull { locationId ->
        val keys = keysByLocation[locationId].orEmpty()
        val rows = keys.mapNotNull { key ->
            val article = stockByKey[key]?.article ?: articles[key.second] ?: return@mapNotNull null
            ContentsArticleRow(
                article = article,
                locationId = locationId,
                stockMilli = stockByKey[key]?.stockMilli ?: 0,
                attributes = catalog.format(article),
                units = unitsByKey[key].orEmpty(),
            )
        }.sortedWith(compareBy<ContentsArticleRow> { it.article.name.lowercase() }.thenBy { it.article.id })
        if (rows.isEmpty()) return@mapNotNull null
        val location = byId.getValue(locationId)
        val relative = options[locationId]?.path?.removePrefix(rootPath)?.removePrefix(" › ").orEmpty()
        ContentsGroup(location, if (locationId == rootId) "" else relative, rows)
    }

    // Article count per sub-location, its own subtree included
    val articlesAt = (stock.map { it.locationId to it.article.id } + items.mapNotNull { item -> item.locationId?.let { it to item.articleId } })
        .groupBy({ it.first }, { it.second })
    val subLocations = children[rootId].orEmpty()
        .sortedWith(compareBy<Location> { it.name.lowercase() }.thenBy { it.id })
        .map { child ->
            val count = subtreeIds(child.id, children).flatMapTo(HashSet()) { articlesAt[it].orEmpty() }.size
            ContentsSubLocation(child, count)
        }

    return LocationContentsUiState(
        loading = false,
        location = root,
        path = rootPath,
        includeSubLocations = includeSubLocations,
        groups = groups,
        subLocations = subLocations,
    )
}

/** [rootId] and its descendants, from the locations grouped by parent id. Each id is visited once. */
private fun subtreeIds(rootId: String, children: Map<String?, List<Location>>): List<String> {
    val result = ArrayList<String>()
    val seen = HashSet<String>()
    val queue = ArrayDeque<String>()
    queue.add(rootId)
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!seen.add(id)) continue
        result += id
        children[id]?.forEach { queue.add(it.id) }
    }
    return result
}

/**
 * The contents of a location, e.g. after scanning its tag. Sub-locations can be opened (a stack: [back]
 * returns to the previous one). [computeDispatcher] builds the contents off the main thread (tests pass their own).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationContentsViewModel(
    rootId: String,
    private val dao: LocationDao,
    attributeDao: AttributeDao,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val stack = MutableStateFlow(listOf(rootId))
    private val includeSubLocations = MutableStateFlow(true)

    private val catalog: Flow<AttributeCatalog> =
        combine(attributeDao.observeDefinitions(), attributeDao.observeActiveLinks(), ::AttributeCatalog)

    private val current: Flow<String> = stack.map { it.last() }.distinctUntilChanged()

    private val locations: Flow<List<Location>> =
        dao.observeLocations().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val state: StateFlow<LocationContentsUiState> = current.flatMapLatest { id ->
        // The stock queries are only subscribed again when the subtree changes, not on every location write
        locations.map { it.subtreeIds(id) }.distinctUntilChanged().flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(LocationContentsUiState(loading = false))
            } else {
                combine(dao.observeArticleStockIn(ids), dao.observeItemsIn(ids), locations, catalog, includeSubLocations) { stock, items, locations, catalog, include ->
                    buildLocationContents(id, locations, stock, items, catalog, include)
                }
            }
        }
    }.combine(stack) { state, stack -> state.copy(canGoBack = stack.size > 1) }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationContentsUiState())

    fun setIncludeSubLocations(include: Boolean) {
        includeSubLocations.value = include
    }

    /** Opens a sub-location; [back] returns here. */
    fun open(locationId: String) {
        stack.update { if (it.last() == locationId) it else it + locationId }
    }

    /** Back to the location opened before. False when this is the first one (the caller closes the screen). */
    fun back(): Boolean {
        if (stack.value.size <= 1) return false
        stack.update { it.dropLast(1) }
        return true
    }
}

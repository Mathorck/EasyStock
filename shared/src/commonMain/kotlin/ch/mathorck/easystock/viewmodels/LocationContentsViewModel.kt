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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val subtree = locations.subtreeIds(rootId)
    val byId = locations.associateBy { it.id }
    // Tree order of the subtree: the depth-first order of the paths
    val ordered = locations.toLocationTree().map { it.location.id }.filter { it in subtree.toSet() }
    val shown = if (includeSubLocations) ordered else listOf(rootId)
    val shownSet = shown.toSet()

    val stockByKey = stock.filter { it.locationId in shownSet }.associateBy { it.locationId to it.article.id }
    val unitsByKey = items.filter { it.locationId in shownSet }.groupBy { it.locationId!! to it.articleId }
    val articles = stock.associate { it.article.id to it.article }

    val groups = shown.mapNotNull { locationId ->
        val keys = (stockByKey.keys + unitsByKey.keys).filter { it.first == locationId }.distinct()
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
    val subLocations = locations.childrenOf(rootId)
        .sortedWith(compareBy<Location> { it.name.lowercase() }.thenBy { it.id })
        .map { child ->
            val count = locations.subtreeIds(child.id).flatMap { articlesAt[it].orEmpty() }.toSet().size
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

/**
 * The contents of a location, e.g. after scanning its tag. Sub-locations can be opened (a stack: [back]
 * returns to the previous one).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationContentsViewModel(
    rootId: String,
    private val dao: LocationDao,
    attributeDao: AttributeDao,
) : ViewModel() {
    private val stack = MutableStateFlow(listOf(rootId))
    private val includeSubLocations = MutableStateFlow(true)

    private val catalog: Flow<AttributeCatalog> =
        combine(attributeDao.observeDefinitions(), attributeDao.observeActiveLinks(), ::AttributeCatalog)

    private val current: Flow<String> = stack.map { it.last() }.distinctUntilChanged()

    val state: StateFlow<LocationContentsUiState> = current.flatMapLatest { id ->
        dao.observeLocations().flatMapLatest { locations ->
            val ids = locations.subtreeIds(id)
            if (ids.isEmpty()) {
                flowOf(LocationContentsUiState(loading = false))
            } else {
                combine(dao.observeArticleStockIn(ids), dao.observeItemsIn(ids), catalog, includeSubLocations) { stock, items, catalog, include ->
                    buildLocationContents(id, locations, stock, items, catalog, include)
                }
            }
        }
    }.combine(stack) { state, stack -> state.copy(canGoBack = stack.size > 1) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocationContentsUiState())

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

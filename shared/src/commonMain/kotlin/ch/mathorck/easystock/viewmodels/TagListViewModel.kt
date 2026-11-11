package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.attributes.AttributeText
import ch.mathorck.easystock.composables.attributes.summaryLine
import ch.mathorck.easystock.composables.nfc.articleSubtitle
import ch.mathorck.easystock.data.db.TagLinkResult
import ch.mathorck.easystock.data.db.TagStore
import ch.mathorck.easystock.data.db.TagTarget
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.nfc.TagEvent
import ch.mathorck.easystock.nfc.tagIdSearchKey
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** One assigned NFC tag: the article or location holding it. */
data class TagRow(
    /** Stored format ([ch.mathorck.easystock.nfc.tagIdOf]); shown with [ch.mathorck.easystock.nfc.formatTagId]. */
    val tagId: String,
    val target: TagTarget,
    val name: String,
    /** The location path ("Garage › Shelf"), or the SKU and properties of the article. Null when there is nothing more to say. */
    val subtitle: String? = null,
    /** Location icon and color keys (null for articles, or the default). */
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
) {
    val isLocation: Boolean get() = target is TagTarget.OfLocation
}

/** The "All / Articles / Locations" chip. */
enum class TagListFilter { ALL, ARTICLES, LOCATIONS }

enum class TagListSort { NAME, TAG_ID }

/**
 * Every tag held by an active article or location, articles and locations together, by name.
 * [locations] are all the active locations (the paths need the untagged parents); only the tagged ones become rows.
 * [attributeLine] is the properties line of an article, null or blank when it has none.
 */
fun buildTagRows(articles: List<Article>, locations: List<Location>, attributeLine: (Article) -> String? = { null }): List<TagRow> {
    val byId = locations.associateBy { it.id }
    val articleRows = articles.mapNotNull { article ->
        val tagId = article.barcode ?: return@mapNotNull null
        if (article.deletedAt != null) return@mapNotNull null
        TagRow(tagId, TagTarget.OfArticle(article.id), article.name, articleSubtitle(article.sku, attributeLine(article)), archived = article.archived)
    }
    val locationRows = buildLocationOptions(locations).mapNotNull { option ->
        val location = byId.getValue(option.id)
        val tagId = location.barcode ?: return@mapNotNull null
        if (location.deletedAt != null) return@mapNotNull null
        TagRow(
            tagId, TagTarget.OfLocation(location.id), location.name,
            subtitle = option.path.takeIf { it != location.name },
            icon = location.icon, color = location.color,
        )
    }
    return (articleRows + locationRows).sortTags(TagListSort.NAME)
}

/**
 * Keeps the rows whose name contains [query] (ignoring case), or whose tag ID contains its hex digits
 * ("de:ad" finds "DEADBEEF01"). A blank query keeps all.
 */
fun List<TagRow>.searchTags(query: String): List<TagRow> {
    val q = query.trim()
    if (q.isEmpty()) return this
    val key = tagIdSearchKey(q)
    return filter { it.name.contains(q, ignoreCase = true) || (key.isNotEmpty() && it.tagId.contains(key)) }
}

fun List<TagRow>.filterTags(filter: TagListFilter): List<TagRow> = when (filter) {
    TagListFilter.ALL -> this
    TagListFilter.ARTICLES -> filterNot { it.isLocation }
    TagListFilter.LOCATIONS -> filter { it.isLocation }
}

/** By name ignoring case (tag ID breaks ties), or by tag ID. */
fun List<TagRow>.sortTags(sort: TagListSort): List<TagRow> = when (sort) {
    TagListSort.NAME -> sortedWith(compareBy<TagRow, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.tagId })
    TagListSort.TAG_ID -> sortedBy { it.tagId }
}

/** The rows shown: searched, filtered and sorted. */
fun List<TagRow>.displayedTags(query: String, filter: TagListFilter, sort: TagListSort): List<TagRow> =
    searchTags(query).filterTags(filter).sortTags(sort)

/** A scanned (or just linked) tag to scroll to. [seq] changes on each request, so the same tag scrolls again. */
data class TagHighlight(val tagId: String, val seq: Int)

/** The picker linking [tagId] to an article or location: an unknown tag, or a tag being moved. */
private data class TagListPickerInput(
    val tagId: String,
    val type: TagPickerType,
    val query: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
)

enum class TagListMessage { READ_FAILED, LOOKUP_FAILED, UNLINK_FAILED, UNLINKED }

data class TagListUiState(
    val loading: Boolean = true,
    /** Every assigned tag, by name (for the counts). */
    val all: List<TagRow> = emptyList(),
    /** [all] searched, filtered and sorted: what the list shows. */
    val rows: List<TagRow> = emptyList(),
    val query: String = "",
    val filter: TagListFilter = TagListFilter.ALL,
    val sort: TagListSort = TagListSort.NAME,
    val highlight: TagHighlight? = null,
    /** A scanned tag no active article or location holds: offer to link it or create one with it. */
    val unknownTagId: String? = null,
    val picker: TagPickerUiState? = null,
    /** The tag the open [picker] links. */
    val pickerTagId: String? = null,
    val moveRequest: TagMoveRequest? = null,
    /** Asking before unlinking this row. */
    val pendingUnlink: TagRow? = null,
    val message: TagListMessage? = null,
) {
    val articleCount: Int get() = all.count { !it.isLocation }
    val locationCount: Int get() = all.count { it.isLocation }

    /** The screen identifies scanned tags: not while a picker or a dialog is open. */
    val acceptsTags: Boolean get() = picker == null && moveRequest == null && pendingUnlink == null
}

/**
 * Settings > NFC tags: every tag held by an active article or location, with search, a type filter, unlink
 * and reassign. A scanned tag scrolls to its row; an unknown one can be linked or used for a new article
 * or location (the screen opens the create forms, then calls [highlightTag]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagListViewModel(
    private val tags: TagStore,
    catalog: Flow<AttributeCatalog> = flowOf(AttributeCatalog(emptyList(), emptyList())),
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TagListFilter.ALL)
    private val sort = MutableStateFlow(TagListSort.NAME)
    private val highlight = MutableStateFlow<TagHighlight?>(null)
    private val unknownTagId = MutableStateFlow<String?>(null)
    private val pickerInput = MutableStateFlow<TagListPickerInput?>(null)
    private val moveRequest = MutableStateFlow<TagMoveRequest?>(null)
    private val pendingUnlink = MutableStateFlow<TagRow?>(null)
    private val message = MutableStateFlow<TagListMessage?>(null)
    private var highlightSeq = 0

    private class Lists(
        val rows: List<TagRow>,
        val catalog: AttributeCatalog,
        val untaggedArticles: List<Article>,
        val locations: List<Location>,
    )

    /** Untagged articles are only collected while the article picker is open. */
    private val untaggedArticles: Flow<List<Article>> =
        pickerInput.map { it?.type == TagPickerType.ARTICLE }.distinctUntilChanged()
            .flatMapLatest { open -> if (open) tags.observeUntaggedArticles() else flowOf(emptyList()) }

    private val lists: Flow<Lists> = combine(tags.observeTaggedArticles(), tags.observeLocations(), catalog, untaggedArticles) { tagged, locations, catalog, untagged ->
        Lists(buildTagRows(tagged, locations) { catalog.format(it).summaryLine() }, catalog, untagged, locations)
    }

    private val filters = combine(query, filter, sort) { q, f, s -> Triple(q, f, s) }
    private val dialogs = combine(highlight, unknownTagId, moveRequest, pendingUnlink, message) { h, u, m, p, msg ->
        Dialogs(h, u, m, p, msg)
    }

    private data class Dialogs(
        val highlight: TagHighlight?,
        val unknownTagId: String?,
        val moveRequest: TagMoveRequest?,
        val pendingUnlink: TagRow?,
        val message: TagListMessage?,
    )

    val state: StateFlow<TagListUiState> = combine(lists, filters, dialogs, pickerInput) { lists, (q, f, s), d, input ->
        TagListUiState(
            loading = false,
            all = lists.rows,
            rows = lists.rows.displayedTags(q, f, s),
            query = q,
            filter = f,
            sort = s,
            highlight = d.highlight,
            unknownTagId = d.unknownTagId,
            picker = input?.let { pickerState(it, lists) },
            pickerTagId = input?.tagId,
            moveRequest = d.moveRequest,
            pendingUnlink = d.pendingUnlink,
            message = d.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TagListUiState())

    private fun pickerState(input: TagListPickerInput, lists: Lists): TagPickerUiState = when (input.type) {
        TagPickerType.ARTICLE -> {
            val attributes: Map<String, List<AttributeText>> = lists.untaggedArticles.associate { it.id to lists.catalog.format(it) }
            TagPickerUiState(
                type = input.type,
                query = input.query,
                articles = lists.untaggedArticles.filterArticles(input.query) { attributes[it.id].orEmpty() },
                attributeLines = attributes.filterValues { it.isNotEmpty() }.mapValues { it.value.summaryLine() },
                noneUntagged = lists.untaggedArticles.isEmpty(),
                saving = input.saving,
                saveFailed = input.failed,
            )
        }
        TagPickerType.LOCATION -> {
            val untagged = untaggedLocationOptions(lists.locations)
            TagPickerUiState(
                type = input.type,
                query = input.query,
                locations = untagged.filterLocations(input.query),
                noneUntagged = untagged.isEmpty(),
                saving = input.saving,
                saveFailed = input.failed,
            )
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: TagListFilter) {
        filter.value = value
    }

    fun onSortChange(value: TagListSort) {
        sort.value = value
    }

    /** A tag was held near the phone while the screen [TagListUiState.acceptsTags]. */
    fun onTag(event: TagEvent) {
        if (!state.value.acceptsTags) return
        when (event) {
            TagEvent.Failed -> message.value = TagListMessage.READ_FAILED
            is TagEvent.Read -> identify(event.tagId)
        }
    }

    private fun identify(tagId: String) {
        if (state.value.all.any { it.tagId == tagId }) {
            highlightTag(tagId)
            return
        }
        // Not in the list (yet): ask the database, the list may lag behind a change
        viewModelScope.launch {
            try {
                if (tags.findOwner(tagId) != null) {
                    highlightTag(tagId)
                } else {
                    highlight.value = null
                    unknownTagId.value = tagId
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = TagListMessage.LOOKUP_FAILED
            }
        }
    }

    /**
     * Scrolls to [tagId] and highlights it. The search and the filter are cleared when they would hide it.
     * Works before the row exists (just linked or created): the screen scrolls once it shows up.
     */
    fun highlightTag(tagId: String) {
        unknownTagId.value = null
        val current = state.value
        val row = current.all.firstOrNull { it.tagId == tagId }
        if (row == null || current.rows.none { it.tagId == tagId }) {
            val shownWithoutSearch = row != null && listOf(row).filterTags(current.filter).isNotEmpty()
            query.value = ""
            if (!shownWithoutSearch) filter.value = TagListFilter.ALL
        }
        highlight.value = TagHighlight(tagId, ++highlightSeq)
    }

    /** The highlight of request [seq] has been shown long enough. A newer one stays. */
    fun clearHighlight(seq: Int) {
        highlight.update { if (it?.seq == seq) null else it }
    }

    fun dismissUnknown() {
        unknownTagId.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    fun requestUnlink(row: TagRow) {
        pendingUnlink.value = row
    }

    fun cancelUnlink() {
        pendingUnlink.value = null
    }

    /** Clears the tag of the confirmed row (its `barcode`), never deleting the row. */
    fun confirmUnlink() {
        val row = pendingUnlink.value ?: return
        pendingUnlink.value = null
        viewModelScope.launch {
            message.value = try {
                tags.unlink(row.target, clock.now().toEpochMilliseconds())
                TagListMessage.UNLINKED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TagListMessage.UNLINK_FAILED
            }
        }
    }

    /** Opens the picker to move the tag of [row] to an untagged article or location ([type]). */
    fun startReassign(row: TagRow, type: TagPickerType) {
        pickerInput.value = TagListPickerInput(row.tagId, type)
    }

    /** Opens the picker to link the unknown tag to an untagged article or location ([type]). */
    fun linkUnknown(type: TagPickerType) {
        val tagId = unknownTagId.value ?: return
        unknownTagId.value = null
        pickerInput.value = TagListPickerInput(tagId, type)
    }

    fun onPickerQuery(value: String) {
        pickerInput.update { it?.copy(query = value) }
    }

    fun closePicker() {
        pickerInput.value = null
        moveRequest.value = null
    }

    /** Links the picker's tag to the article or location [id]. A tag held elsewhere asks first ([TagMoveRequest]). */
    fun pick(id: String) {
        val input = pickerInput.value ?: return
        if (input.saving) return
        val picker = state.value.picker
        val (target, name) = when (input.type) {
            TagPickerType.ARTICLE -> TagTarget.OfArticle(id) to picker?.articles?.firstOrNull { it.id == id }?.name
            TagPickerType.LOCATION -> TagTarget.OfLocation(id) to picker?.locations?.firstOrNull { it.id == id }?.name
        }
        link(input.tagId, target, confirmedOwner = null, targetName = name.orEmpty())
    }

    /** The user agreed to move the tag from its owner. */
    fun confirmMove() {
        val request = moveRequest.value ?: return
        moveRequest.value = null
        link(request.tagId, request.target, confirmedOwner = request.owner.target, targetName = request.targetName)
    }

    /** Keeps the tag where it is; the picker stays open to choose another one. */
    fun cancelMove() {
        moveRequest.value = null
    }

    private fun link(tagId: String, target: TagTarget, confirmedOwner: TagTarget?, targetName: String) {
        pickerInput.update { it?.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                when (val result = tags.link(target, tagId, confirmedOwner, clock.now().toEpochMilliseconds())) {
                    TagLinkResult.Linked -> {
                        pickerInput.value = null
                        highlightTag(tagId)
                    }
                    is TagLinkResult.LinkedElsewhere -> {
                        pickerInput.update { it?.copy(saving = false) }
                        moveRequest.value = TagMoveRequest(tagId, target, result.owner, targetName)
                    }
                    // Deleted meanwhile: it leaves the picker, choose another one
                    TagLinkResult.TargetGone -> pickerInput.update { it?.copy(saving = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pickerInput.update { it?.copy(saving = false, failed = true) }
            }
        }
    }
}

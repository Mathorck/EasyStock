package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.TagLinkResult
import ch.mathorck.easystock.data.db.TagOwner
import ch.mathorck.easystock.data.db.TagStore
import ch.mathorck.easystock.data.db.TagTarget
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.enums.ScanState
import ch.mathorck.easystock.nfc.NfcAvailability
import ch.mathorck.easystock.nfc.TagEvent
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
import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.composables.attributes.AttributeText
import ch.mathorck.easystock.composables.attributes.matches
import ch.mathorck.easystock.composables.attributes.summaryLine
import kotlin.time.Clock

/**
 * Keeps the articles whose name, SKU or one of their [attributes] values (see [matches]) contains [query],
 * ignoring case. A blank query keeps all.
 */
fun List<Article>.filterArticles(query: String, attributes: (Article) -> List<AttributeText> = { emptyList() }): List<Article> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.sku?.contains(q, ignoreCase = true) == true || attributes(it).matches(q) }
}

/** Keeps the locations whose name or path contains [query], ignoring case. A blank query keeps all. */
fun List<LocationOption>.filterLocations(query: String): List<LocationOption> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
}

/** The locations without a tag, with their full path (the parents may be tagged). */
fun untaggedLocationOptions(locations: List<Location>): List<LocationOption> {
    val untagged = locations.filterTo(HashSet()) { it.barcode == null }.mapTo(HashSet()) { it.id }
    return buildLocationOptions(locations).filter { it.id in untagged }
}

/** Where the scan flow is. */
sealed interface ScanPhase {
    data object Ready : ScanPhase

    /** Looking the tag up. */
    data class Reading(val tagId: String) : ScanPhase

    /** No active article or location holds [tagId]: link it to one, or create one with it. */
    data class NotFound(val tagId: String) : ScanPhase

    /** The tag could not be read, or the lookup failed. */
    data object Error : ScanPhase

    /** The detail of [articleId] is shown. */
    data class Opened(val articleId: String) : ScanPhase

    /** The contents of [locationId] are shown. */
    data class OpenedLocation(val locationId: String) : ScanPhase
}

/** What the picker links the unknown tag to. */
enum class TagPickerType { ARTICLE, LOCATION }

/** The picker of articles or locations without a tag, to link the unknown tag. */
data class TagPickerUiState(
    val type: TagPickerType = TagPickerType.ARTICLE,
    val query: String = "",
    /** Untagged articles matching [query], for [TagPickerType.ARTICLE]. Null while loading. */
    val articles: List<Article>? = null,
    /** The properties line of each article of [articles] that has properties, by article id ("Longueur 50 m · ..."). */
    val attributeLines: Map<String, String> = emptyMap(),
    /** Untagged locations matching [query], for [TagPickerType.LOCATION]. Null while loading. */
    val locations: List<LocationOption>? = null,
    /** True when nothing at all is untagged (as opposed to none matching the query). */
    val noneUntagged: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** Still loading the list of its [type]. */
    val loading: Boolean get() = if (type == TagPickerType.ARTICLE) articles == null else locations == null
}

/** Linking asks first: [owner] already holds [tagId], linking moves it to [target]. */
data class TagMoveRequest(val tagId: String, val target: TagTarget, val owner: TagOwner, val targetName: String = "")

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Ready,
    /** What the scan circle shows. */
    val scanState: ScanState = ScanState.Ready,
    val picker: TagPickerUiState? = null,
    val moveRequest: TagMoveRequest? = null,
) {
    /** The unknown tag, while [ScanState.NotFound]. */
    val unknownTagId: String? get() = (phase as? ScanPhase.NotFound)?.tagId

    /** The article whose detail is shown instead of the scan circle. */
    val openArticleId: String? get() = (phase as? ScanPhase.Opened)?.articleId

    /** The location whose contents are shown instead of the scan circle. */
    val openLocationId: String? get() = (phase as? ScanPhase.OpenedLocation)?.locationId

    /** The page takes tags: not while a lookup runs, a detail is open or a dialog is shown. */
    val acceptsTags: Boolean
        get() = phase !is ScanPhase.Reading && phase !is ScanPhase.Opened && phase !is ScanPhase.OpenedLocation &&
            picker == null && moveRequest == null
}

/** The circle state: the phase, or the NFC availability while waiting. */
fun scanStateOf(phase: ScanPhase, availability: NfcAvailability): ScanState = when (phase) {
    ScanPhase.Ready -> when (availability) {
        NfcAvailability.Unavailable -> ScanState.NfcUnavailable
        NfcAvailability.Disabled -> ScanState.NfcDisabled
        NfcAvailability.Enabled, NfcAvailability.Unknown -> ScanState.Ready
    }
    is ScanPhase.Reading -> ScanState.Reading
    is ScanPhase.NotFound -> ScanState.NotFound
    ScanPhase.Error -> ScanState.Error
    is ScanPhase.Opened, is ScanPhase.OpenedLocation -> ScanState.Success
}

/**
 * The Scan page: a tag read opens its article, or the contents of its location. An unknown tag can be
 * linked to an article or a location without tag, or used for a new one (the page opens the create
 * form, then calls [openArticle] or [openLocation]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModel(
    private val tags: TagStore,
    availability: Flow<NfcAvailability>,
    private val clock: Clock = Clock.System,
    /** Formats the properties of the articles in the picker. Only collected while the article picker is open. */
    private val catalog: Flow<AttributeCatalog> = flowOf(AttributeCatalog(emptyList(), emptyList())),
) : ViewModel() {
    private val phase = MutableStateFlow<ScanPhase>(ScanPhase.Ready)

    /** Null while the picker is closed. */
    private val pickerQuery = MutableStateFlow<String?>(null)
    private val pickerType = MutableStateFlow(TagPickerType.ARTICLE)
    private val pickerStatus = MutableStateFlow(FormSaveStatus())
    private val moveRequest = MutableStateFlow<TagMoveRequest?>(null)

    /** The untagged rows of the open picker's type. */
    private class PickerRows(
        val type: TagPickerType,
        val articles: List<Article>? = null,
        /** The values of each article of [articles], formatted once per emission. */
        val attributes: Map<String, List<AttributeText>> = emptyMap(),
        val locations: List<LocationOption>? = null,
    )

    /** Null while the picker is closed. */
    private val pickerRows: Flow<PickerRows?> =
        combine(pickerQuery.map { it != null }.distinctUntilChanged(), pickerType) { open, type -> type.takeIf { open } }
            .distinctUntilChanged()
            .flatMapLatest { type ->
                when (type) {
                    null -> flowOf(null)
                    TagPickerType.ARTICLE -> combine(tags.observeUntaggedArticles(), catalog) { articles, catalog ->
                        PickerRows(type, articles = articles, attributes = articles.associate { it.id to catalog.format(it) })
                    }
                    TagPickerType.LOCATION -> tags.observeLocations().map { PickerRows(type, locations = untaggedLocationOptions(it)) }
                }
            }

    private val picker: Flow<TagPickerUiState?> = combine(pickerRows, pickerQuery, pickerType, pickerStatus) { rows, query, type, status ->
        query?.let {
            val current = rows?.takeIf { r -> r.type == type }
            TagPickerUiState(
                type = type,
                query = it,
                articles = current?.articles?.filterArticles(it) { a -> current.attributes[a.id].orEmpty() },
                attributeLines = current?.attributes.orEmpty().mapNotNull { (id, texts) -> texts.takeIf { t -> t.isNotEmpty() }?.let { t -> id to t.summaryLine() } }.toMap(),
                locations = current?.locations?.filterLocations(it),
                noneUntagged = current != null && current.articles.orEmpty().isEmpty() && current.locations.orEmpty().isEmpty(),
                saving = status.saving,
                saveFailed = status.failed,
            )
        }
    }

    val state: StateFlow<ScanUiState> = combine(phase, availability, picker, moveRequest) { phase, availability, picker, move ->
        ScanUiState(phase, scanStateOf(phase, availability), picker, move)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScanUiState())

    /** A tag was held near the phone while the page [ScanUiState.acceptsTags]. */
    fun onTag(event: TagEvent) {
        val current = phase.value
        if (current is ScanPhase.Reading || current is ScanPhase.Opened || current is ScanPhase.OpenedLocation ||
            pickerQuery.value != null || moveRequest.value != null
        ) return
        when (event) {
            TagEvent.Failed -> phase.value = ScanPhase.Error
            is TagEvent.Read -> lookUp(event.tagId)
        }
    }

    private fun lookUp(tagId: String) {
        val reading = ScanPhase.Reading(tagId)
        phase.value = reading
        viewModelScope.launch {
            val result = try {
                when (val owner = tags.findOwner(tagId)) {
                    is TagOwner.OfArticle -> ScanPhase.Opened(owner.article.id)
                    is TagOwner.OfLocation -> ScanPhase.OpenedLocation(owner.location.id)
                    null -> ScanPhase.NotFound(tagId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanPhase.Error
            }
            // Ignored when the page was reset meanwhile
            phase.update { if (it == reading) result else it }
        }
    }

    /** Back to waiting for a tag (try again, cancel, or back from the article or location). */
    fun reset() {
        phase.value = ScanPhase.Ready
        closePicker()
        moveRequest.value = null
    }

    /** Shows the detail of [articleId], e.g. the article just created with the tag. */
    fun openArticle(articleId: String) {
        closePicker()
        moveRequest.value = null
        phase.value = ScanPhase.Opened(articleId)
    }

    /** Shows the contents of [locationId], e.g. the location just created with the tag. */
    fun openLocation(locationId: String) {
        closePicker()
        moveRequest.value = null
        phase.value = ScanPhase.OpenedLocation(locationId)
    }

    /** Opens the picker of untagged articles or locations ([type]) for the unknown tag. */
    fun openPicker(type: TagPickerType = TagPickerType.ARTICLE) {
        if (phase.value !is ScanPhase.NotFound) return
        pickerStatus.value = FormSaveStatus()
        pickerType.value = type
        pickerQuery.value = ""
    }

    fun onPickerQuery(query: String) {
        pickerQuery.update { it?.let { query } }
    }

    fun closePicker() {
        pickerQuery.value = null
        pickerStatus.value = FormSaveStatus()
    }

    /** Links the unknown tag to the article or location [id] (picker type), then opens it. */
    fun linkTo(id: String) {
        val tagId = state.value.unknownTagId ?: return
        if (pickerStatus.value.saving || pickerQuery.value == null) return
        val picker = state.value.picker
        val (target, name) = when (pickerType.value) {
            TagPickerType.ARTICLE -> TagTarget.OfArticle(id) to picker?.articles?.firstOrNull { it.id == id }?.name
            TagPickerType.LOCATION -> TagTarget.OfLocation(id) to picker?.locations?.firstOrNull { it.id == id }?.name
        }
        link(tagId, target, confirmedOwner = null, targetName = name.orEmpty())
    }

    /** The user agreed to move the tag from its owner. */
    fun confirmMove() {
        val request = moveRequest.value ?: return
        moveRequest.value = null
        link(request.tagId, request.target, confirmedOwner = request.owner.target, targetName = request.targetName)
    }

    fun cancelMove() {
        moveRequest.value = null
    }

    private fun link(tagId: String, target: TagTarget, confirmedOwner: TagTarget?, targetName: String) {
        pickerStatus.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                when (val result = tags.link(target, tagId, confirmedOwner, clock.now().toEpochMilliseconds())) {
                    TagLinkResult.Linked -> when (target) {
                        is TagTarget.OfArticle -> openArticle(target.id)
                        is TagTarget.OfLocation -> openLocation(target.id)
                    }
                    is TagLinkResult.LinkedElsewhere -> {
                        pickerStatus.update { it.copy(saving = false) }
                        moveRequest.value = TagMoveRequest(tagId, target, result.owner, targetName)
                    }
                    // Deleted meanwhile: it leaves the list, pick another one
                    TagLinkResult.TargetGone -> pickerStatus.update { it.copy(saving = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pickerStatus.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}

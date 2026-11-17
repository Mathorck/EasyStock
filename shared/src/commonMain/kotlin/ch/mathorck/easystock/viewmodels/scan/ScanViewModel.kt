package ch.mathorck.easystock.viewmodels.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.matches
import ch.mathorck.easystock.composables.attributes.summaryLine
import ch.mathorck.easystock.data.dao.tags.TagLinkResult
import ch.mathorck.easystock.data.dao.tags.TagOwner
import ch.mathorck.easystock.data.dao.tags.TagStore
import ch.mathorck.easystock.data.dao.tags.TagTarget
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.ScanState
import ch.mathorck.easystock.enums.nfc.NfcAvailability
import ch.mathorck.easystock.enums.tags.TagPickerType
import ch.mathorck.easystock.services.attributes.AttributeCatalog
import ch.mathorck.easystock.services.attributes.AttributeText
import ch.mathorck.easystock.services.nfc.TagEvent
import ch.mathorck.easystock.viewmodels.article.FormSaveStatus
import ch.mathorck.easystock.viewmodels.location.LocationOption
import ch.mathorck.easystock.viewmodels.location.buildLocationOptions
import ch.mathorck.easystock.viewmodels.tags.TagMoveRequest
import ch.mathorck.easystock.viewmodels.tags.TagPickerUiState
import kotlin.time.Clock
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

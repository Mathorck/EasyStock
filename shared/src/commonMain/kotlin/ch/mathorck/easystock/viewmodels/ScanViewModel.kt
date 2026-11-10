package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.ArticleTags
import ch.mathorck.easystock.data.db.TagLinkResult
import ch.mathorck.easystock.data.model.Article
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
import kotlin.time.Clock

/** Keeps the articles whose name or SKU contains [query], ignoring case. A blank query keeps all. */
fun List<Article>.filterArticles(query: String): List<Article> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.sku?.contains(q, ignoreCase = true) == true }
}

/** Where the scan flow is. */
sealed interface ScanPhase {
    data object Ready : ScanPhase

    /** Looking the tag up. */
    data class Reading(val tagId: String) : ScanPhase

    /** No active article holds [tagId]: link it or create an article. */
    data class NotFound(val tagId: String) : ScanPhase

    /** The tag could not be read, or the lookup failed. */
    data object Error : ScanPhase

    /** The detail of [articleId] is shown. */
    data class Opened(val articleId: String) : ScanPhase
}

/** The picker of articles without a tag, to link the unknown tag. */
data class TagPickerUiState(
    val query: String = "",
    /** Untagged articles matching [query]. Null while loading. */
    val articles: List<Article>? = null,
    /** True when no article at all is untagged (as opposed to none matching the query). */
    val noneUntagged: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Linking asks first: [owner] already holds [tagId], linking moves it to [articleId]. */
data class TagMoveRequest(val tagId: String, val articleId: String, val owner: Article)

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

    /** The page takes tags: not while a lookup runs, the detail is open or a dialog is shown. */
    val acceptsTags: Boolean
        get() = phase !is ScanPhase.Reading && phase !is ScanPhase.Opened && picker == null && moveRequest == null
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
    is ScanPhase.Opened -> ScanState.Success
}

/**
 * The Scan page: a tag read opens its article, an unknown tag can be linked to an article without tag,
 * or used for a new article (the page opens the create form, then calls [openArticle]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModel(
    private val tags: ArticleTags,
    availability: Flow<NfcAvailability>,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val phase = MutableStateFlow<ScanPhase>(ScanPhase.Ready)

    /** Null while the picker is closed. */
    private val pickerQuery = MutableStateFlow<String?>(null)
    private val pickerStatus = MutableStateFlow(FormSaveStatus())
    private val moveRequest = MutableStateFlow<TagMoveRequest?>(null)

    private val picker: Flow<TagPickerUiState?> = combine(
        pickerQuery.map { it != null }.distinctUntilChanged().flatMapLatest { open ->
            if (open) tags.observeUntagged() else flowOf(null)
        },
        pickerQuery,
        pickerStatus,
    ) { untagged, query, status ->
        query?.let {
            TagPickerUiState(
                query = it,
                articles = untagged?.filterArticles(it),
                noneUntagged = untagged?.isEmpty() == true,
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
        if (current is ScanPhase.Reading || current is ScanPhase.Opened || pickerQuery.value != null || moveRequest.value != null) return
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
                tags.findByTag(tagId)?.let { ScanPhase.Opened(it.id) } ?: ScanPhase.NotFound(tagId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanPhase.Error
            }
            // Ignored when the page was reset meanwhile
            phase.update { if (it == reading) result else it }
        }
    }

    /** Back to waiting for a tag (try again, cancel, or back from the article). */
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

    fun openPicker() {
        if (phase.value !is ScanPhase.NotFound) return
        pickerStatus.value = FormSaveStatus()
        pickerQuery.value = ""
    }

    fun onPickerQuery(query: String) {
        pickerQuery.update { it?.let { query } }
    }

    fun closePicker() {
        pickerQuery.value = null
        pickerStatus.value = FormSaveStatus()
    }

    /** Links the unknown tag to [articleId], then opens it. */
    fun linkTo(articleId: String) {
        val tagId = state.value.unknownTagId ?: return
        if (pickerStatus.value.saving) return
        link(tagId, articleId, confirmedOwnerId = null)
    }

    /** The user agreed to move the tag from its owner. */
    fun confirmMove() {
        val request = moveRequest.value ?: return
        moveRequest.value = null
        link(request.tagId, request.articleId, confirmedOwnerId = request.owner.id)
    }

    fun cancelMove() {
        moveRequest.value = null
    }

    private fun link(tagId: String, articleId: String, confirmedOwnerId: String?) {
        pickerStatus.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                when (val result = tags.link(articleId, tagId, confirmedOwnerId, clock.now().toEpochMilliseconds())) {
                    TagLinkResult.Linked -> openArticle(articleId)
                    is TagLinkResult.LinkedElsewhere -> {
                        pickerStatus.update { it.copy(saving = false) }
                        moveRequest.value = TagMoveRequest(tagId, articleId, result.owner)
                    }
                    // Deleted meanwhile: it leaves the list, pick another one
                    TagLinkResult.ArticleGone -> pickerStatus.update { it.copy(saving = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pickerStatus.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}

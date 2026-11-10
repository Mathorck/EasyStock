package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.ArticleTags
import ch.mathorck.easystock.data.db.TagLinkResult
import ch.mathorck.easystock.nfc.TagEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** The "Link tag" sheet of an article. Closed while [TagLinkUiState.waiting] is false and nothing is asked. */
data class TagLinkUiState(
    /** The sheet is open and waits for a tag. */
    val waiting: Boolean = false,
    /** The last tag held near the phone could not be read. */
    val readFailed: Boolean = false,
    /** The tag read is on another article: ask before moving it. */
    val moveRequest: TagMoveRequest? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** The last "Remove tag" failed (shown on the detail screen). */
    val removeFailed: Boolean = false,
) {
    /** The sheet takes tags: not while saving or asking. */
    val acceptsTags: Boolean get() = waiting && moveRequest == null && !saving
}

/** Links, replaces and removes the NFC tag of one article. Replacing is linking a new tag. */
class TagLinkViewModel(
    private val articleId: String,
    private val tags: ArticleTags,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(TagLinkUiState())
    val state: StateFlow<TagLinkUiState> = _state.asStateFlow()

    /** Opens the sheet waiting for a tag (link, or replace the current one). */
    fun start() {
        _state.value = TagLinkUiState(waiting = true)
    }

    fun cancel() {
        _state.value = TagLinkUiState()
    }

    fun onTag(event: TagEvent) {
        if (!_state.value.acceptsTags) return
        when (event) {
            TagEvent.Failed -> _state.update { it.copy(readFailed = true, saveFailed = false) }
            is TagEvent.Read -> link(event.tagId, confirmedOwnerId = null)
        }
    }

    /** The user agreed to move the tag from its owner to this article. */
    fun confirmMove() {
        val request = _state.value.moveRequest ?: return
        _state.update { it.copy(moveRequest = null) }
        link(request.tagId, confirmedOwnerId = request.owner.id)
    }

    /** Keeps the tag on its owner; the sheet waits for another tag. */
    fun cancelMove() {
        _state.update { it.copy(moveRequest = null) }
    }

    private fun link(tagId: String, confirmedOwnerId: String?) {
        _state.update { it.copy(saving = true, saveFailed = false, readFailed = false) }
        viewModelScope.launch {
            try {
                when (val result = tags.link(articleId, tagId, confirmedOwnerId, clock.now().toEpochMilliseconds())) {
                    // Also when the article is deleted meanwhile: the detail screen closes on its own
                    TagLinkResult.Linked, TagLinkResult.ArticleGone -> _state.value = TagLinkUiState()
                    is TagLinkResult.LinkedElsewhere -> _state.update {
                        it.copy(saving = false, moveRequest = TagMoveRequest(tagId, articleId, result.owner))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** Removes the tag of the article (the caller asks for confirmation first). */
    fun removeTag() {
        _state.update { it.copy(removeFailed = false) }
        viewModelScope.launch {
            try {
                tags.unlink(articleId, clock.now().toEpochMilliseconds())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(removeFailed = true) }
            }
        }
    }
}

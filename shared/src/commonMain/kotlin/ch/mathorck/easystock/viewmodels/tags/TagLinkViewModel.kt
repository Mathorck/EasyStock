package ch.mathorck.easystock.viewmodels.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dao.tags.TagLinkResult
import ch.mathorck.easystock.data.dao.tags.TagStore
import ch.mathorck.easystock.data.dao.tags.TagTarget
import ch.mathorck.easystock.services.nfc.TagEvent
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Links, replaces and removes the NFC tag of one article or location. Replacing is linking a new tag. */
class TagLinkViewModel(
    private val target: TagTarget,
    private val tags: TagStore,
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
            is TagEvent.Read -> link(event.tagId, confirmedOwner = null)
        }
    }

    /** The user agreed to move the tag from its owner to this target. */
    fun confirmMove() {
        val request = _state.value.moveRequest ?: return
        _state.update { it.copy(moveRequest = null) }
        link(request.tagId, confirmedOwner = request.owner.target)
    }

    /** Keeps the tag on its owner; the sheet waits for another tag. */
    fun cancelMove() {
        _state.update { it.copy(moveRequest = null) }
    }

    private fun link(tagId: String, confirmedOwner: TagTarget?) {
        _state.update { it.copy(saving = true, saveFailed = false, readFailed = false) }
        viewModelScope.launch {
            try {
                when (val result = tags.link(target, tagId, confirmedOwner, clock.now().toEpochMilliseconds())) {
                    // Also when the target is deleted meanwhile: its screen closes on its own
                    TagLinkResult.Linked, TagLinkResult.TargetGone -> _state.value = TagLinkUiState()
                    is TagLinkResult.LinkedElsewhere -> _state.update {
                        it.copy(saving = false, moveRequest = TagMoveRequest(tagId, target, result.owner))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** Removes the tag of the target (the caller asks for confirmation first). */
    fun removeTag() {
        _state.update { it.copy(removeFailed = false) }
        viewModelScope.launch {
            try {
                tags.unlink(target, clock.now().toEpochMilliseconds())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(removeFailed = true) }
            }
        }
    }
}

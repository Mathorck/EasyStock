package ch.mathorck.easystock.viewmodels.tags

import ch.mathorck.easystock.enums.tags.TagListFilter
import ch.mathorck.easystock.enums.tags.TagListMessage
import ch.mathorck.easystock.enums.tags.TagListSort

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

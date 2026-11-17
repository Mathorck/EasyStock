package ch.mathorck.easystock.viewmodels.tags

import ch.mathorck.easystock.enums.tags.TagPickerType

/** The picker linking [tagId] to an article or location: an unknown tag, or a tag being moved. */
internal data class TagListPickerInput(
    val tagId: String,
    val type: TagPickerType,
    val query: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
)

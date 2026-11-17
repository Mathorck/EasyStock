package ch.mathorck.easystock.viewmodels.tags

/** The "Link tag" sheet of an article or a location. Closed while [TagLinkUiState.waiting] is false and nothing is asked. */
data class TagLinkUiState(
    /** The sheet is open and waits for a tag. */
    val waiting: Boolean = false,
    /** The last tag held near the phone could not be read. */
    val readFailed: Boolean = false,
    /** The tag read is on another article or location: ask before moving it. */
    val moveRequest: TagMoveRequest? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** The last "Remove tag" failed (shown on the detail screen). */
    val removeFailed: Boolean = false,
) {
    /** The sheet takes tags: not while saving or asking. */
    val acceptsTags: Boolean get() = waiting && moveRequest == null && !saving
}

package ch.mathorck.easystock.viewmodels.tags

/** A scanned (or just linked) tag to scroll to. [seq] changes on each request, so the same tag scrolls again. */
data class TagHighlight(val tagId: String, val seq: Int)

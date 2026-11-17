package ch.mathorck.easystock.data.model

/**
 * A date value that is in the past, on a date attribute with `flag_when_past` (computed, never stored).
 * [targetId] is the article id, or the item id when [isItem]. [dateValue] is the stored "YYYY-MM-DD".
 */
data class PastDate(
    val articleId: String,
    val articleName: String,
    val targetId: String,
    val isItem: Boolean,
    val attributeKey: String,
    val label: String,
    val dateValue: String,
    /** The definition asks for a notification (`notify_when_past`). */
    val notify: Boolean,
)

package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.enums.stock.MovementReason

/** One line of the movement history. [occurredAt] is formatted in the local time zone. */
data class MovementRow(
    val id: String,
    val occurredAt: String,
    val reason: MovementReason,
    val location: String,
    val quantityMilli: Long,
    val comment: String?,
    /** Display name of who made it (server mode). Null in standalone mode or when the author is unknown. */
    val author: String? = null,
)

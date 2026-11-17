package ch.mathorck.easystock.services.review

import ch.mathorck.easystock.data.model.article.PendingUnit
import ch.mathorck.easystock.enums.article.ItemStatus

/** One change applied on finish. Every change only adds stock_movement rows (and updates the unit). */
sealed interface ReviewChange {
    val lineId: String
    val articleId: String

    /** Quantity correction: one INVENTORY movement of [deltaMilli] at [locationId]. */
    data class Correction(
        override val lineId: String,
        override val articleId: String,
        val locationId: String,
        val currentMilli: Long,
        val deltaMilli: Long,
    ) : ReviewChange

    /** Unit in stock at [fromLocationId], found at [toLocationId]: TRANSFER -1 / +1. */
    data class MoveUnit(
        override val lineId: String,
        override val articleId: String,
        val itemId: String,
        val fromLocationId: String,
        val toLocationId: String,
    ) : ReviewChange

    /**
     * Unit out of stock (lost, lent) or with no location, found at [locationId]: +1. Lost uses the "found"
     * action (INVENTORY), lent the "return" action (RETURN); both make it available.
     * An in-stock unit with no location keeps its status (INVENTORY).
     */
    data class BringBack(
        override val lineId: String,
        override val articleId: String,
        val itemId: String,
        val locationId: String,
        val from: ItemStatus,
    ) : ReviewChange

    /** A new unit found during the review: creates the unit at [locationId] with one INVENTORY +1 movement. */
    data class CreateUnit(
        override val lineId: String,
        override val articleId: String,
        val locationId: String,
        val unit: PendingUnit,
    ) : ReviewChange

    /** Unit missing and resolved as lost: the "lost" action (-1 LOSS when in stock, status only when lent). */
    data class LoseUnit(
        override val lineId: String,
        override val articleId: String,
        val itemId: String,
        val from: ItemStatus,
        val locationId: String?,
    ) : ReviewChange
}

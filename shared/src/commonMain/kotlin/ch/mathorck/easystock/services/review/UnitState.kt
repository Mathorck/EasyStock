package ch.mathorck.easystock.services.review

import ch.mathorck.easystock.enums.article.ItemStatus

/** Stored state of a unit when the review is finished. */
data class UnitState(
    val status: ItemStatus,
    /** Where the movements put it, null when out of stock (lent, lost). */
    val locationId: String?,
    val deleted: Boolean,
)

package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.enums.article.StockActionError
import ch.mathorck.easystock.enums.attributes.AttributeError

data class StockActionErrors(
    val quantity: StockActionError? = null,
    val reason: StockActionError? = null,
    val location: StockActionError? = null,
    val locationName: StockActionError? = null,
    val serial: StockActionError? = null,
    /** The unit is gone or its status no longer allows the action. */
    val unit: StockActionError? = null,
    /** Item attribute values that are not valid, keyed by attribute key. */
    val attributes: Map<String, AttributeError> = emptyMap(),
) {
    val isValid: Boolean get() = listOf(quantity, reason, location, locationName, serial, unit).all { it == null } && attributes.isEmpty()
}

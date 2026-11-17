package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.stock.UnitAction
import ch.mathorck.easystock.services.attributes.AttributeRow

// ---------- Units (display) ----------

/** One serial unit. [path] is null when the unit is out of stock (lent, lost). */
data class UnitRow(
    val itemId: String,
    val serial: String?,
    val status: ItemStatus,
    val path: String?,
    /** Location of its last movement, the default place when it comes back. */
    val lastLocationId: String?,
    val actions: List<UnitAction>,
    val canMove: Boolean,
    /** Values this unit sets itself, different from the article's. */
    val properties: List<AttributeRow> = emptyList(),
)

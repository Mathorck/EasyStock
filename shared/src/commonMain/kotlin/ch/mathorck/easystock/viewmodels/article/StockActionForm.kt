package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.article.StockActionKind
import ch.mathorck.easystock.enums.location.LocationChoice
import ch.mathorck.easystock.enums.stock.MovementReason
import ch.mathorck.easystock.enums.stock.UnitAction
import kotlinx.serialization.json.JsonObject

/** Raw input of the action sheet, as typed. */
data class StockActionForm(
    val kind: StockActionKind,
    /** Location of the row the action was started from (fixed). Null for "add to a location". */
    val sourceLocationId: String? = null,
    /** The unit, for [StockActionKind.MOVE_UNIT], [StockActionKind.UNIT_STATUS] and [StockActionKind.EDIT_UNIT]. */
    val itemId: String? = null,
    val unitAction: UnitAction? = null,
    val targetMode: LocationChoice = LocationChoice.EXISTING,
    val targetLocationId: String? = null,
    val newLocationName: String = "",
    val newLocationParentId: String? = null,
    val quantity: String = "",
    val reason: MovementReason? = defaultReason(kind),
    val serial: String = "",
    /** Unit properties ([StockActionKind.ADD_UNIT] and [StockActionKind.EDIT_UNIT]). */
    val itemStatus: ItemStatus = ItemStatus.AVAILABLE,
    /** Unit-specific attribute values, overriding the article ones. Same keys as `article.attributes`. */
    val itemAttributes: JsonObject? = null,
    val archived: Boolean = false,
    val comment: String = "",
) {
    /** The user picks a destination (existing or new). */
    val needsTarget: Boolean
        get() = when (kind) {
            StockActionKind.ADD -> sourceLocationId == null
            StockActionKind.REMOVE -> false
            StockActionKind.MOVE, StockActionKind.ADD_UNIT, StockActionKind.MOVE_UNIT -> true
            StockActionKind.UNIT_STATUS -> unitAction == UnitAction.RETURN || unitAction == UnitAction.FOUND
            StockActionKind.EDIT_UNIT -> false
        }

    /** The sheet edits the properties of a unit (serial, status, attributes). */
    val hasUnitProperties: Boolean get() = kind == StockActionKind.ADD_UNIT || kind == StockActionKind.EDIT_UNIT

    val needsQuantity: Boolean get() = kind == StockActionKind.ADD || kind == StockActionKind.REMOVE || kind == StockActionKind.MOVE
}

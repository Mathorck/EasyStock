package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.enums.location.LocationError
import ch.mathorck.easystock.viewmodels.stock.StockLocationNode

/**
 * The open dialog. [editing] is null for a new location (its current row while editing, so its tag is
 * up to date). [parents]: where it can go (never itself or below). [tagId]: the NFC tag a new location gets.
 */
data class LocationDialogState(
    val editing: Location?,
    val form: LocationForm,
    val tagId: String? = null,
    val error: LocationError?,
    val parents: List<StockLocationNode>,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = error == null && !saving
}

package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.viewmodels.stock.StockLocationNode
import ch.mathorck.easystock.viewmodels.stock.toLocationTree

/** The active locations, indexed once per database change (not on every dialog keystroke). */
internal class LocationIndex(val locations: List<Location>) {
    val byId: Map<String, Location> = locations.associateBy { it.id }
    val children: Map<String?, List<Location>> = locations.groupBy { it.parentId }
    val tree: List<StockLocationNode> = locations.toLocationTree()
}

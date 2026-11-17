package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.data.model.location.Location

/** A location with its depth in the hierarchy (0 = root), for an indented picker. */
data class StockLocationNode(val location: Location, val depth: Int)

package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

/** A row of the location tree. [articleCount]: articles with stock directly here. */
data class LocationRow(val location: Location, val depth: Int, val articleCount: Int, val childCount: Int)

package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

/** Delete waiting for the user's confirmation. [descendantCount]: sub-locations deleted with it. */
data class LocationDeleteRequest(val location: Location, val descendantCount: Int)

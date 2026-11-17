package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

/** A direct sub-location, with the number of articles in it and below. */
data class ContentsSubLocation(val location: Location, val articleCount: Int)

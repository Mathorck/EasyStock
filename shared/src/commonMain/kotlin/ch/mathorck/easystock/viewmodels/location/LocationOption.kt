package ch.mathorck.easystock.viewmodels.location

/** A location with its full path, e.g. "Room › Shelf". [depth] is 0 for top level. */
data class LocationOption(val id: String, val name: String, val path: String, val depth: Int)

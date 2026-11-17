package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

/**
 * Paths and tree order of [locations], built once and shared by the rows of one emission.
 * [locations] should include soft-deleted ones so every movement location has a path.
 */
class LocationPaths(locations: List<Location>) {
    // buildLocationOptions is already sorted by path (parents before children, ignoring case)
    private val options = buildLocationOptions(locations)
    val order: Map<String, Int> = options.withIndex().associate { (index, option) -> option.id to index }
    val paths: Map<String, String> = options.associate { it.id to it.path }
    val byId: Map<String, Location> = locations.associateBy { it.id }
}

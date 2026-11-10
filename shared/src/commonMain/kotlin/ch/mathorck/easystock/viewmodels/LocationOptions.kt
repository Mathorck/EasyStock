package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Location

// Max length from docs/schema.dbml
const val MAX_LOCATION_NAME_LENGTH = 100

/** A location with its full path, e.g. "Room › Shelf". [depth] is 0 for top level. */
data class LocationOption(val id: String, val name: String, val path: String, val depth: Int)

private const val MAX_LOCATION_DEPTH = 16

/**
 * Builds the location paths ("Room › Shelf › Bin"), sorted as a tree: each parent is followed by its children.
 * A missing parent (deleted) makes the location top level. A cycle or a very deep chain is cut.
 */
fun buildLocationOptions(locations: List<Location>): List<LocationOption> {
    val byId = locations.associateBy { it.id }
    fun segments(location: Location): List<String> {
        val names = mutableListOf(location.name)
        val seen = mutableSetOf(location.id)
        var parentId = location.parentId
        while (parentId != null && names.size < MAX_LOCATION_DEPTH) {
            val parent = byId[parentId] ?: break
            if (!seen.add(parent.id)) break
            names.add(parent.name)
            parentId = parent.parentId
        }
        return names.asReversed()
    }
    val segmentComparator = Comparator<List<String>> { a, b ->
        for (i in 0 until minOf(a.size, b.size)) {
            val c = a[i].compareTo(b[i], ignoreCase = true)
            if (c != 0) return@Comparator c
        }
        a.size.compareTo(b.size)
    }
    return locations
        .map { it to segments(it) }
        .sortedWith(compareBy(segmentComparator) { it.second })
        .map { (location, path) ->
            LocationOption(location.id, location.name, path.joinToString(" › "), path.size - 1)
        }
}

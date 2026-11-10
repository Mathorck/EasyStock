package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Location

/** Why a location write was refused. Nothing is saved. */
enum class LocationError { REQUIRED, TOO_LONG, DUPLICATE, CYCLE, NOT_FOUND, PARENT_NOT_FOUND, HAS_STOCK }

/**
 * Checks a location name. [siblings]: the locations with the same parent (only the ones that are not
 * [editingId] are compared). Names are unique among active siblings, ignoring case.
 */
fun validateLocationName(name: String, siblings: List<Location>, editingId: String? = null): LocationError? {
    val value = name.trim()
    return when {
        value.isEmpty() -> LocationError.REQUIRED
        value.length > MAX_LOCATION_NAME_LENGTH -> LocationError.TOO_LONG
        siblings.any { it.id != editingId && it.deletedAt == null && it.name.trim().equals(value, ignoreCase = true) } ->
            LocationError.DUPLICATE
        else -> null
    }
}

/** The locations whose parent is [parentId] (null = top level). */
fun List<Location>.childrenOf(parentId: String?): List<Location> = filter { it.parentId == parentId }

/**
 * Ids of [rootId] and all its descendants, parents before children. Empty if [rootId] is unknown.
 * Safe on corrupt data: each id is visited once.
 */
fun List<Location>.subtreeIds(rootId: String): List<String> {
    if (none { it.id == rootId }) return emptyList()
    val children = groupBy { it.parentId }
    val result = ArrayList<String>()
    val seen = HashSet<String>()
    val queue = ArrayDeque<String>()
    queue.add(rootId)
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!seen.add(id)) continue
        result += id
        children[id].orEmpty().forEach { queue.add(it.id) }
    }
    return result
}

/** True if moving [id] under [newParentId] makes a cycle: the parent is the location itself or one of its descendants. */
fun List<Location>.wouldCreateCycle(id: String, newParentId: String?): Boolean =
    newParentId != null && newParentId in subtreeIds(id)

/** The locations [id] can be moved under: everything except itself and its descendants. */
fun List<Location>.validParentsFor(id: String): List<Location> {
    val excluded = subtreeIds(id).toSet()
    return filter { it.id !in excluded }
}

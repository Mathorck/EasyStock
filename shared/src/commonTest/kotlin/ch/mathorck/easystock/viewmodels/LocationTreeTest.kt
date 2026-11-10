package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationTreeTest {
    private fun loc(id: String, parentId: String? = null) =
        Location(id = id, name = id, parentId = parentId, createdAt = 0, updatedAt = 0)

    private fun tree(vararg locations: Location) = locations.toList().toLocationTree().map { it.location.id to it.depth }

    @Test
    fun childrenFollowTheirParentIndented() {
        // Input sorted by name, like StockDao.observeLocations().
        val result = tree(loc("bin", "shelf"), loc("garage"), loc("room"), loc("shelf", "room"))

        assertEquals(listOf("garage" to 0, "room" to 0, "shelf" to 1, "bin" to 2), result)
    }

    @Test
    fun siblingsKeepInputOrder() {
        val result = tree(loc("a", "room"), loc("b", "room"), loc("room"))

        assertEquals(listOf("room" to 0, "a" to 1, "b" to 1), result)
    }

    @Test
    fun missingParentMakesARoot() {
        val result = tree(loc("orphan", "deleted"), loc("room"))

        assertEquals(listOf("orphan" to 0, "room" to 0), result)
    }

    @Test
    fun cycleIsCutAndEveryLocationShownOnce() {
        val result = tree(loc("a", "b"), loc("b", "a"))

        assertEquals(listOf("a" to 0, "b" to 1), result)
    }

    @Test
    fun emptyListGivesEmptyTree() {
        assertEquals(emptyList(), tree())
    }
}

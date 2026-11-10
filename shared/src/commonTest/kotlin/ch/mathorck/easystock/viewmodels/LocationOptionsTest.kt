package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationOptionsTest {
    private fun location(id: String, name: String = id, parentId: String? = null) =
        Location(id = id, name = name, parentId = parentId, createdAt = 0, updatedAt = 0)

    @Test
    fun locationPathsFollowTheHierarchy() {
        val options = buildLocationOptions(
            listOf(
                location("bin", "Bin 2", parentId = "shelf"),
                location("shelf", "Shelf A", parentId = "room"),
                location("room", "Workshop"),
                location("attic", "Attic"),
            ),
        )
        assertEquals(listOf("Attic", "Workshop", "Workshop › Shelf A", "Workshop › Shelf A › Bin 2"), options.map { it.path })
        assertEquals(listOf(0, 0, 1, 2), options.map { it.depth })
    }

    @Test
    fun locationWithMissingParentIsTopLevel() {
        val options = buildLocationOptions(listOf(location("shelf", "Shelf", parentId = "deleted")))
        assertEquals("Shelf", options.single().path)
        assertEquals(0, options.single().depth)
    }

    @Test
    fun locationCycleDoesNotLoop() {
        val options = buildLocationOptions(listOf(location("a", "A", parentId = "b"), location("b", "B", parentId = "a")))
        assertEquals(setOf("B › A", "A › B"), options.map { it.path }.toSet())
    }
}

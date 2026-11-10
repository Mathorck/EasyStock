package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationLogicTest {
    private fun loc(id: String, parentId: String? = null, name: String = id) =
        Location(id = id, name = name, parentId = parentId, createdAt = 0, updatedAt = 0)

    // room > shelf > bin, room > cabinet, garage
    private val all = listOf(loc("room"), loc("shelf", "room"), loc("bin", "shelf"), loc("cabinet", "room"), loc("garage"))

    @Test
    fun subtreeHasRootAndAllDescendants() {
        assertEquals(setOf("room", "shelf", "bin", "cabinet"), all.subtreeIds("room").toSet())
        assertEquals(listOf("room"), all.subtreeIds("room").take(1))
        assertEquals(listOf("garage"), all.subtreeIds("garage"))
        assertEquals(emptyList(), all.subtreeIds("unknown"))
    }

    @Test
    fun subtreeSurvivesACycle() {
        val cyclic = listOf(loc("a", "b"), loc("b", "a"))
        assertEquals(listOf("a", "b"), cyclic.subtreeIds("a"))
    }

    @Test
    fun cannotMoveUnderItselfOrADescendant() {
        assertTrue(all.wouldCreateCycle("room", "room"))
        assertTrue(all.wouldCreateCycle("room", "bin"))
        assertTrue(all.wouldCreateCycle("shelf", "bin"))
    }

    @Test
    fun otherMovesAreAllowed() {
        assertFalse(all.wouldCreateCycle("bin", "room"))
        assertFalse(all.wouldCreateCycle("shelf", "garage"))
        assertFalse(all.wouldCreateCycle("bin", null))
        assertFalse(all.wouldCreateCycle("room", null))
    }

    @Test
    fun validParentsExcludeTheSubtree() {
        assertEquals(setOf("room", "cabinet", "garage"), all.validParentsFor("shelf").map { it.id }.toSet())
    }

    @Test
    fun nameIsRequiredAndTrimmed() {
        assertEquals(LocationError.REQUIRED, validateLocationName("   ", emptyList()))
        assertNull(validateLocationName("  Shelf  ", emptyList()))
    }

    @Test
    fun nameIsLimitedToTheSchemaLength() {
        assertNull(validateLocationName("a".repeat(MAX_LOCATION_NAME_LENGTH), emptyList()))
        assertEquals(LocationError.TOO_LONG, validateLocationName("a".repeat(MAX_LOCATION_NAME_LENGTH + 1), emptyList()))
    }

    @Test
    fun nameIsUniqueAmongSiblingsIgnoringCase() {
        val siblings = listOf(loc("a", name = "Shelf A"), loc("b", name = "Shelf B"))
        assertEquals(LocationError.DUPLICATE, validateLocationName(" shelf a ", siblings))
        assertNull(validateLocationName("Shelf C", siblings))
    }

    @Test
    fun editedLocationDoesNotClashWithItself() {
        val siblings = listOf(loc("a", name = "Shelf A"))
        assertNull(validateLocationName("SHELF A", siblings, editingId = "a"))
    }

    @Test
    fun childrenOfFiltersByParent() {
        assertEquals(listOf("room", "garage"), all.childrenOf(null).map { it.id })
        assertEquals(listOf("shelf", "cabinet"), all.childrenOf("room").map { it.id })
    }
}

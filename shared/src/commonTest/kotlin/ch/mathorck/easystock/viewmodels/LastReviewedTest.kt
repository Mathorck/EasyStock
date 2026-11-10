package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals

class LastReviewedTest {
    private fun location(id: String, parent: String? = null) = Location(id = id, name = id, parentId = parent, createdAt = 0, updatedAt = 0)

    private val locations = listOf(location("room"), location("shelf", "room"), location("bin", "shelf"), location("van"))

    @Test
    fun aReviewCoversTheSubLocations() {
        val result = lastReviewedByLocation(locations, mapOf("room" to 100L))

        assertEquals(mapOf("room" to 100L, "shelf" to 100L, "bin" to 100L), result)
    }

    @Test
    fun theLatestOfTheLocationAndItsAncestorsWins() {
        val result = lastReviewedByLocation(locations, mapOf("room" to 100L, "shelf" to 300L, "bin" to 200L))

        assertEquals(100L, result["room"])
        assertEquals(300L, result["shelf"])
        assertEquals(300L, result["bin"])
        assertEquals(null, result["van"])
    }

    @Test
    fun aCycleDoesNotLoop() {
        val cycle = listOf(location("a", "b"), location("b", "a"))

        assertEquals(mapOf("a" to 5L, "b" to 5L), lastReviewedByLocation(cycle, mapOf("a" to 5L)))
    }
}

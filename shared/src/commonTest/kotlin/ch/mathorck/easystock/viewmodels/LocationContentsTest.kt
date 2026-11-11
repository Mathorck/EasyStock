package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.composables.attributes.AttributeCatalog
import ch.mathorck.easystock.data.db.LocationArticleStock
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Tracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationContentsTest {
    private val catalog = AttributeCatalog(emptyList(), emptyList())

    // Garage, Workshop > Rack > Crate (with ropes and a pulley), Workshop > Bench
    private val locations = listOf(
        place("garage", "Garage"),
        place("ws", "Workshop"),
        place("rack", "Rack", parentId = "ws"),
        place("crate", "Crate", parentId = "rack", barcode = "CC01"),
        place("bench", "Bench", parentId = "ws"),
    )
    private val rope = tagged("rope", "Corde dynamique")
    private val cord = tagged("cord", "Cordelette")
    private val pulley = tagged("pulley", "Pulley").copy(tracking = Tracking.SERIAL)
    private val drill = tagged("drill", "Drill")

    private val stock = listOf(
        LocationArticleStock(rope, "crate", 2_000),
        LocationArticleStock(cord, "crate", 1_500),
        LocationArticleStock(pulley, "crate", 1_000),
        LocationArticleStock(drill, "rack", 1_000),
        LocationArticleStock(rope, "bench", 500),
        LocationArticleStock(drill, "garage", 3_000),
    )
    private val items = listOf(
        Item(id = "p1", articleId = "pulley", serialNumber = "P-1", locationId = "crate", createdAt = 0, updatedAt = 0),
    )

    private fun build(root: String, include: Boolean = true) =
        buildLocationContents(root, locations, stock, items, catalog, include)

    @Test
    fun locationShowsItsArticlesWithStockAndUnits() {
        val state = build("crate")

        assertEquals("Workshop › Rack › Crate", state.path)
        assertEquals(1, state.groups.size)
        val group = state.groups.single()
        assertEquals("", group.path)
        // By name: "Corde dynamique" before "Cordelette"
        assertEquals(listOf("rope", "cord", "pulley"), group.rows.map { it.article.id })
        val pulleyRow = group.rows.single { it.article.id == "pulley" }
        assertTrue(pulleyRow.isSerial)
        assertEquals(listOf("p1"), pulleyRow.units.map { it.id })
        assertEquals(1_500, group.rows.single { it.article.id == "cord" }.stockMilli)
        assertEquals(3, state.articleCount)
        assertTrue(state.subLocations.isEmpty())
    }

    @Test
    fun subLocationsAreIncludedInTreeOrderWithRelativePaths() {
        val state = build("ws")

        assertEquals(listOf("rack", "crate", "bench"), state.groups.map { it.location.id })
        assertEquals(listOf("Rack", "Rack › Crate", "Bench"), state.groups.map { it.path })
        // The same article in two places is two rows
        assertEquals(5, state.articleCount)
        // Direct children by name, counting distinct articles in their subtree
        assertEquals(listOf("bench" to 1, "rack" to 4), state.subLocations.map { it.location.id to it.articleCount })
        // Other branches are not listed
        assertTrue(state.groups.none { it.location.id == "garage" })
    }

    @Test
    fun withoutSubLocationsOnlyTheLocationItselfIsListed() {
        val state = build("ws", include = false)

        assertTrue(state.isEmpty)
        assertEquals(2, state.subLocations.size)

        val rack = build("rack", include = false)
        assertEquals(listOf("drill"), rack.groups.single().rows.map { it.article.id })
    }

    @Test
    fun deletedLocationIsGone() {
        val state = build("missing")

        assertNull(state.location)
        assertTrue(state.isEmpty)
    }

    @Test
    fun reviewJumpsToTheLocationGroupOrItsFirstSubGroup() {
        val scope = buildLocationOptions(locations.filter { it.id != "garage" })
        val groups = listOf("rack", "crate", "bench").map { ReviewGroup(it, it, it, emptyList()) }

        assertEquals("crate", groupForLocation("crate", scope, groups))
        // No line directly at the workshop: its first sub-location in tree order
        assertEquals("rack", groupForLocation("ws", scope, groups))
        assertEquals("crate", groupForLocation("rack", scope, groups.filter { it.locationId != "rack" }))
        // Outside the scope, or nothing in the review there
        assertNull(groupForLocation("garage", scope, groups))
        assertNull(groupForLocation("bench", scope, groups.filter { it.locationId != "bench" }))
    }
}

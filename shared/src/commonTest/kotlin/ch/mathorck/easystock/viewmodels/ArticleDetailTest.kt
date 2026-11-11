package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.db.InfoFlag
import ch.mathorck.easystock.data.db.LocationStock
import ch.mathorck.easystock.data.db.MovementWithLocation
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleDetailTest {
    private fun loc(id: String, name: String = id, parentId: String? = null, deletedAt: Long? = null) =
        Location(id = id, name = name, parentId = parentId, createdAt = 0, updatedAt = 0, deletedAt = deletedAt)

    private val locations = listOf(
        loc("van", "Van"),
        loc("room", "Workshop"),
        loc("shelf", "Shelf A", parentId = "room"),
        loc("bin", "Bin 3", parentId = "shelf", deletedAt = 5),
    )

    @Test
    fun locationRowsSkipZeroAndSortByPath() {
        val rows = locationStockRows(
            listOf(LocationStock("van", 2_000), LocationStock("shelf", 0), LocationStock("bin", 1_500), LocationStock("room", -500)),
            locations,
        )

        assertEquals(
            listOf(
                LocationStockRow("van", "Van", 2_000),
                LocationStockRow("room", "Workshop", -500),
                LocationStockRow("bin", "Workshop › Shelf A › Bin 3", 1_500),
            ),
            rows,
        )
    }

    @Test
    fun unknownLocationGoesLastWithItsId() {
        val rows = locationStockRows(listOf(LocationStock("gone", 1_000), LocationStock("van", 1_000)), locations)

        assertEquals(listOf("Van", "gone"), rows.map { it.path })
    }

    private fun movement(id: String, location: String, quantity: Long, occurredAt: Long, comment: String? = null) =
        StockMovement(
            id = id, articleId = "a", locationId = location, quantityMilli = quantity,
            reason = MovementReason.CONSUMPTION, comment = comment, occurredAt = occurredAt, createdAt = occurredAt,
        )

    @Test
    fun movementRowsKeepOrderAndUsePaths() {
        val rows = movementRows(
            listOf(
                MovementWithLocation(movement("m2", "shelf", -1_000, 1_762_786_620_000, comment = "Job 12"), "Shelf A"),
                MovementWithLocation(movement("m1", "gone", 3_000, 0, comment = "  "), "Old bin"),
            ),
            locations,
            TimeZone.UTC,
        )

        assertEquals(listOf("m2", "m1"), rows.map { it.id })
        assertEquals("Workshop › Shelf A", rows[0].location)
        assertEquals("2025-11-10 14:57", rows[0].occurredAt)
        assertEquals("Job 12", rows[0].comment)
        // Location missing from the list: falls back to the joined name. Blank comment: hidden.
        assertEquals("Old bin", rows[1].location)
        assertNull(rows[1].comment)
    }

    @Test
    fun dateTimeIsFormattedInTheGivenZone() {
        assertEquals("1970-01-01 00:00", formatDateTime(0, TimeZone.UTC))
        assertEquals("1970-01-01 01:00", formatDateTime(0, TimeZone.of("Europe/Zurich")))
        assertEquals("2026-07-01 02:05", formatDateTime(1_782_864_300_000, TimeZone.of("Europe/Zurich")))
    }

    @Test
    fun signedQuantity() {
        assertEquals("+1.5", formatSignedQuantity(1_500))
        assertEquals("-2", formatSignedQuantity(-2_000))
        assertEquals("0", formatSignedQuantity(0))
    }

    // ---------- Info to fix flags ----------

    private fun flag(id: String, flaggedAt: Long, fixedAt: Long? = null, note: String? = "Change the box", location: String = "shelf", serial: String? = null) =
        InfoFlag(id, note, location, "name-$location", serial, flaggedAt, fixedAt)

    @Test
    fun openFlagsLeaveOutFixedOnesAndUsePaths() {
        val rows = openInfoFlagRows(
            listOf(
                flag("f2", 1_762_786_620_000, serial = "SN-1"),
                flag("f1", 1_000, fixedAt = 2_000),
                flag("f0", 0, note = "  ", location = "gone"),
            ),
            locations,
            TimeZone.UTC,
        )

        assertEquals(
            listOf(
                InfoFlagRow("f2", "Change the box", "Workshop › Shelf A", "SN-1", "2025-11-10 14:57"),
                // Blank note: none. Unknown location: the joined name.
                InfoFlagRow("f0", null, "name-gone", null, "1970-01-01 00:00"),
            ),
            rows,
        )
    }

    @Test
    fun historyMergesMovementsAndFlagEventsNewestFirst() {
        val rows = historyRows(
            movements = listOf(
                MovementWithLocation(movement("m3", "van", -1_000, occurredAt = 5_000), "Van"),
                MovementWithLocation(movement("m2", "shelf", 2_000, occurredAt = 3_000), "Shelf A"),
                MovementWithLocation(movement("m1", "shelf", 1_000, occurredAt = 1_000), "Shelf A"),
            ),
            flags = listOf(
                flag("open", flaggedAt = 3_000),
                flag("fixed", flaggedAt = 2_000, fixedAt = 6_000),
            ),
            locations = locations,
            timeZone = TimeZone.UTC,
        )

        assertEquals(
            listOf("fixed-fixed", "movement-m3", "movement-m2", "flagged-open", "flagged-fixed", "movement-m1"),
            rows.map { it.key },
        )
        val fixed = rows[0] as HistoryRow.InfoFixed
        assertEquals("1970-01-01 00:00", fixed.at)
        assertEquals("Change the box", fixed.flag.note)
        val flagged = rows[3] as HistoryRow.InfoFlagged
        assertEquals("Workshop › Shelf A", flagged.flag.location)
    }

    @Test
    fun historyWithOnlyFlagsIsNotEmpty() {
        val rows = historyRows(emptyList(), listOf(flag("f", flaggedAt = 10)), locations, TimeZone.UTC)

        assertEquals(listOf("flagged-f"), rows.map { it.key })
    }
}

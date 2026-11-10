package ch.mathorck.easystock.review

import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.data.model.StockReviewLine
import ch.mathorck.easystock.viewmodels.subtreeIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewLogicTest {
    private fun line(
        id: String,
        article: String = "a",
        location: String = "shelf",
        item: String? = null,
        expected: Long = 5000,
        counted: Long? = null,
        result: ReviewResult = ReviewResult.UNCHECKED,
        found: String? = null,
        flagged: Boolean = false,
        createdAt: Long = 1,
    ) = StockReviewLine(
        id = id, reviewId = "r", articleId = article, itemId = item, locationId = location,
        expectedMilli = expected, countedMilli = counted, result = result, foundLocationId = found,
        infoFlagged = flagged, createdAt = createdAt, updatedAt = createdAt,
    )

    private fun unitLine(id: String, item: String, result: ReviewResult, location: String = "shelf", expected: Long = 1000, found: String? = null) =
        line(id, article = "tool", location = location, item = item, expected = expected, result = result, found = found)

    private fun inputs(
        vararg lines: StockReviewLine,
        stock: Map<Pair<String, String>, Long> = emptyMap(),
        units: Map<String, UnitState> = emptyMap(),
        articles: Set<String> = setOf("a", "b", "tool"),
        locations: Set<String> = setOf("room", "shelf", "bin"),
    ) = FinishInputs(lines.toList(), stock, units, articles, locations)

    private fun ids(): () -> String {
        var n = 0
        return { "id${++n}" }
    }

    // ---------- Scope ----------

    @Test
    fun scopeIsTheLocationAndAllItsDescendants() {
        fun loc(id: String, parent: String? = null) = Location(id = id, name = id, parentId = parent, createdAt = 0, updatedAt = 0)
        val all = listOf(loc("room"), loc("shelf", "room"), loc("bin", "shelf"), loc("garage"))

        assertEquals(setOf("room", "shelf", "bin"), all.subtreeIds("room").toSet())
        assertEquals(setOf("shelf", "bin"), all.subtreeIds("shelf").toSet())
    }

    // ---------- Status ----------

    @Test
    fun lineStatus() {
        assertEquals(LineStatus.UNCHECKED, line("1").status())
        assertEquals(LineStatus.OK, line("1", counted = 5000, result = ReviewResult.COUNTED).status())
        assertEquals(LineStatus.DISCREPANCY, line("1", counted = 4000, result = ReviewResult.COUNTED).status())
        assertEquals(LineStatus.OK, unitLine("1", "i", ReviewResult.PRESENT).status())
        assertEquals(LineStatus.DISCREPANCY, unitLine("1", "i", ReviewResult.PRESENT, expected = 0).status())
        assertEquals(LineStatus.DISCREPANCY, unitLine("1", "i", ReviewResult.MISSING).status())
        assertEquals(LineStatus.DISCREPANCY, unitLine("1", "i", ReviewResult.ELSEWHERE, found = "bin").status())
    }

    @Test
    fun progressCountsCheckedDiscrepanciesAndFlags() {
        val lines = listOf(
            line("1"),
            line("2", counted = 5000, result = ReviewResult.COUNTED, flagged = true),
            line("3", counted = 1000, result = ReviewResult.COUNTED),
            unitLine("4", "i", ReviewResult.MISSING),
        )

        assertEquals(ReviewProgress(total = 4, checked = 3, discrepancies = 2, flagged = 1), lines.progress())
        assertEquals(listOf("1"), lines.filter { it.matches(LineFilter.UNCHECKED) }.map { it.id })
        assertEquals(listOf("3", "4"), lines.filter { it.matches(LineFilter.DISCREPANCIES) }.map { it.id })
        assertEquals(listOf("2"), lines.filter { it.matches(LineFilter.FLAGGED) }.map { it.id })
    }

    // ---------- Sync ----------

    @Test
    fun syncAddsALinePerPositionWithTheExpectedQuantity() {
        val positions = listOf(
            ReviewPosition("a", null, "shelf", 2500),
            ReviewPosition("tool", "i1", "bin", 1000),
        )

        val sync = syncReviewLines("r", emptyList(), positions, now = 7, newId = ids())

        assertEquals(2, sync.inserts.size)
        val quantity = sync.inserts.first { it.itemId == null }
        assertEquals(2500, quantity.expectedMilli)
        assertEquals(ReviewResult.UNCHECKED, quantity.result)
        val unit = sync.inserts.first { it.itemId == "i1" }
        assertEquals(1000, unit.expectedMilli)
        assertEquals("bin", unit.locationId)
        assertTrue(sync.updates.isEmpty())
    }

    @Test
    fun syncKeepsExistingLinesAndRefreshesOnlyUncheckedQuantities() {
        val existing = listOf(
            line("1", article = "a", location = "shelf", expected = 2000),
            line("2", article = "b", location = "shelf", expected = 2000, counted = 2000, result = ReviewResult.COUNTED),
            unitLine("3", "i1", ReviewResult.UNCHECKED, location = "shelf"),
        )
        val positions = listOf(
            ReviewPosition("a", null, "shelf", 3000),
            ReviewPosition("b", null, "shelf", 9000),
            // The unit moved to the bin: still one line
            ReviewPosition("tool", "i1", "bin", 1000),
        )

        val sync = syncReviewLines("r", existing, positions, now = 7, newId = ids())

        assertTrue(sync.inserts.isEmpty())
        assertEquals(listOf("1" to 3000L), sync.updates.map { it.id to it.expectedMilli })
    }

    @Test
    fun syncSetsZeroWhenTheStockOfAnUncheckedLineIsGone() {
        val sync = syncReviewLines("r", listOf(line("1", expected = 2000)), emptyList(), now = 7, newId = ids())

        assertEquals(0, sync.updates.single().expectedMilli)
    }

    // ---------- Finish: quantities ----------

    @Test
    fun countedDifferenceBecomesAnInventoryCorrection() {
        val plan = planReviewFinish(
            inputs(line("1", expected = 5000, counted = 3500, result = ReviewResult.COUNTED), stock = mapOf(("a" to "shelf") to 5000L)),
            emptyMap(),
        )

        val change = plan.changes.single() as ReviewChange.Correction
        assertEquals(-1500, change.deltaMilli)
        assertEquals("shelf", change.locationId)
        assertTrue(plan.drifted.isEmpty())
    }

    @Test
    fun correctCountChangesNothing() {
        val plan = planReviewFinish(
            inputs(line("1", expected = 5000, counted = 5000, result = ReviewResult.COUNTED), stock = mapOf(("a" to "shelf") to 5000L)),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
    }

    /** Stock used after the count is kept: the correction is relative to what was expected when counting. */
    @Test
    fun movementsAfterTheCountAreKept() {
        val plan = planReviewFinish(
            inputs(line("1", expected = 5000, counted = 4000, result = ReviewResult.COUNTED), stock = mapOf(("a" to "shelf") to 3000L)),
            emptyMap(),
        )

        assertEquals(-1000, (plan.changes.single() as ReviewChange.Correction).deltaMilli)
        assertEquals(setOf("1"), plan.drifted)
    }

    @Test
    fun correctionNeverGoesBelowZero() {
        val plan = planReviewFinish(
            inputs(line("1", expected = 5000, counted = 1000, result = ReviewResult.COUNTED), stock = mapOf(("a" to "shelf") to 2000L)),
            emptyMap(),
        )

        assertEquals(-2000, (plan.changes.single() as ReviewChange.Correction).deltaMilli)
        assertEquals(setOf("1"), plan.clamped)
    }

    @Test
    fun uncheckedLinesChangeNothing() {
        val plan = planReviewFinish(inputs(line("1"), unitLine("2", "i1", ReviewResult.UNCHECKED)), emptyMap())

        assertTrue(plan.changes.isEmpty())
        assertEquals(2, plan.unchecked)
    }

    @Test
    fun deletedArticleIsSkipped() {
        val plan = planReviewFinish(
            inputs(line("1", counted = 0, result = ReviewResult.COUNTED), stock = mapOf(("a" to "shelf") to 5000L), articles = emptySet()),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
        assertEquals(listOf(SkippedLine("1", SkipReason.GONE)), plan.skipped)
    }

    // ---------- Finish: units ----------

    @Test
    fun unitFoundElsewhereIsTransferred() {
        val plan = planReviewFinish(
            inputs(
                unitLine("1", "i1", ReviewResult.ELSEWHERE, found = "bin"),
                units = mapOf("i1" to UnitState(ItemStatus.AVAILABLE, "shelf", deleted = false)),
            ),
            emptyMap(),
        )

        assertEquals(ReviewChange.MoveUnit("1", "tool", "i1", "shelf", "bin"), plan.changes.single())
    }

    @Test
    fun unitPresentWhereExpectedChangesNothing() {
        val plan = planReviewFinish(
            inputs(unitLine("1", "i1", ReviewResult.PRESENT), units = mapOf("i1" to UnitState(ItemStatus.REPAIR, "shelf", false))),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun unitPresentButMovedSinceIsSkipped() {
        val plan = planReviewFinish(
            inputs(unitLine("1", "i1", ReviewResult.PRESENT), units = mapOf("i1" to UnitState(ItemStatus.AVAILABLE, "bin", false))),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
        assertEquals(listOf(SkippedLine("1", SkipReason.CHANGED)), plan.skipped)
    }

    @Test
    fun lostOrLentUnitFoundHereComesBack() {
        val plan = planReviewFinish(
            inputs(
                unitLine("1", "i1", ReviewResult.PRESENT, expected = 0),
                unitLine("2", "i2", ReviewResult.PRESENT, expected = 0, location = "bin"),
                units = mapOf(
                    "i1" to UnitState(ItemStatus.LOST, null, false),
                    "i2" to UnitState(ItemStatus.LENT, null, false),
                ),
            ),
            emptyMap(),
        )

        assertEquals(
            listOf(
                ReviewChange.BringBack("1", "tool", "i1", "shelf", ItemStatus.LOST),
                ReviewChange.BringBack("2", "tool", "i2", "bin", ItemStatus.LENT),
            ),
            plan.changes,
        )
    }

    @Test
    fun missingUnitIsLostByDefaultOrKept() {
        val units = mapOf(
            "i1" to UnitState(ItemStatus.AVAILABLE, "shelf", false),
            "i2" to UnitState(ItemStatus.AVAILABLE, "shelf", false),
        )
        val lines = arrayOf(unitLine("1", "i1", ReviewResult.MISSING), unitLine("2", "i2", ReviewResult.MISSING))

        val plan = planReviewFinish(inputs(*lines, units = units), mapOf("2" to MissingResolution.KEEP))

        assertEquals(listOf(ReviewChange.LoseUnit("1", "tool", "i1", ItemStatus.AVAILABLE, "shelf")), plan.changes)
    }

    @Test
    fun missingUnitAlreadyLostOrMovedSinceIsNotLostAgain() {
        val plan = planReviewFinish(
            inputs(
                unitLine("1", "i1", ReviewResult.MISSING),
                unitLine("2", "i2", ReviewResult.MISSING),
                units = mapOf(
                    "i1" to UnitState(ItemStatus.LOST, null, false),
                    "i2" to UnitState(ItemStatus.AVAILABLE, "bin", false),
                ),
            ),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
        assertEquals(listOf(SkippedLine("2", SkipReason.CHANGED)), plan.skipped)
    }

    @Test
    fun foundAtADeletedLocationIsSkipped() {
        val plan = planReviewFinish(
            inputs(
                unitLine("1", "i1", ReviewResult.ELSEWHERE, found = "gone"),
                units = mapOf("i1" to UnitState(ItemStatus.AVAILABLE, "shelf", false)),
            ),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
        assertEquals(SkipReason.GONE, plan.skipped.single().reason)
    }

    @Test
    fun eachUnitIsHandledOnce() {
        val plan = planReviewFinish(
            inputs(
                unitLine("1", "i1", ReviewResult.ELSEWHERE, found = "bin"),
                unitLine("2", "i1", ReviewResult.PRESENT, location = "bin", expected = 0),
                units = mapOf("i1" to UnitState(ItemStatus.AVAILABLE, "shelf", false)),
            ),
            emptyMap(),
        )

        assertEquals(1, plan.changes.size)
    }

    @Test
    fun deletedUnitIsSkipped() {
        val plan = planReviewFinish(
            inputs(unitLine("1", "i1", ReviewResult.MISSING), units = mapOf("i1" to UnitState(ItemStatus.AVAILABLE, "shelf", deleted = true))),
            emptyMap(),
        )

        assertTrue(plan.changes.isEmpty())
        assertEquals(SkipReason.GONE, plan.skipped.single().reason)
    }
}

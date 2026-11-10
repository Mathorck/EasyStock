package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.data.model.ReviewStatus
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.review.MissingResolution
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var reviews: ReviewDao
    private lateinit var actions: StockActionDao
    private var ids = 0
    private val newId: () -> String = { "id${ids++}" }

    // room > shelf > bin, van (outside the scope of room)
    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        reviews = db.reviewDao()
        actions = db.stockActionDao()
        val stock = db.stockDao()
        stock.insertLocation(Location(id = "room", name = "Room", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "shelf", name = "Shelf", parentId = "room", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "bin", name = "Bin", parentId = "shelf", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "van", name = "Van", createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "bolt", name = "Bolt", createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "drill", name = "Drill", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
        actions.adjust(movement("bolt", "shelf", 10_000))
        actions.adjust(movement("bolt", "van", 4_000))
        addUnit("d1", "bin")
        addUnit("d2", "shelf")
        addUnit("d3", "van")
    }

    @After
    fun tearDown() = db.close()

    private fun movement(article: String, location: String, quantity: Long) = StockMovement(
        id = newId(), articleId = article, locationId = location, quantityMilli = quantity,
        reason = MovementReason.PURCHASE, occurredAt = 1, createdAt = 1,
    )

    private suspend fun addUnit(id: String, location: String) {
        actions.addItem(
            Item(id = id, articleId = "drill", serialNumber = "SN-$id", locationId = location, createdAt = 1, updatedAt = 1),
            movement("drill", location, UNIT_MILLI).copy(itemId = id),
        )
    }

    private suspend fun allMovements(): List<StockMovement> = db.backupDao().snapshot().movements
    private suspend fun unit(id: String): Item = db.backupDao().snapshot().items.single { it.id == id }
    private suspend fun lines(reviewId: String) = reviews.observeLines(reviewId).first()
    private suspend fun boltLine(reviewId: String) = lines(reviewId).single { it.articleId == "bolt" }
    private suspend fun unitLine(reviewId: String, item: String) = lines(reviewId).single { it.itemId == item }

    @Test
    fun reviewCoversTheLocationAndItsDescendants() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)

        val lines = lines(id)
        assertEquals(setOf("bolt@shelf", "d1@bin", "d2@shelf"), lines.map { "${it.itemId ?: it.articleId}@${it.locationId}" }.toSet())
        assertEquals(10_000, boltLine(id).expectedMilli)
        assertTrue(lines.all { it.result == ReviewResult.UNCHECKED })
    }

    @Test
    fun oneOpenReviewPerLocationIsResumed() = runBlocking {
        val first = reviews.startReview("room", now = 100, newId = newId)
        actions.adjust(movement("bolt", "bin", 2_000))

        val again = reviews.startReview("room", now = 200, newId = newId)

        assertEquals(first, again)
        assertEquals(1, reviews.observeReviews().first().size)
        // Stock that appeared meanwhile gets its line
        assertTrue(lines(first).any { it.articleId == "bolt" && it.locationId == "bin" })
    }

    @Test
    fun countAppliesOnlyTheDifferenceAsInventoryMovement() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        val before = allMovements()
        reviews.setCount(boltLine(id).id, 8_500, now = 110)
        // Used after the count: kept
        actions.adjust(movement("bolt", "shelf", -1_000).copy(reason = MovementReason.CONSUMPTION))

        val plan = reviews.finishReview(id, emptyMap(), comment = "Review room", now = 120, newId = newId)

        assertEquals(1, plan.changes.size)
        assertEquals(7_500, actions.stockAt("bolt", "shelf"))
        val added = allMovements() - before.toSet()
        val correction = added.single { it.reason == MovementReason.INVENTORY }
        assertEquals(-1_500, correction.quantityMilli)
        assertEquals("Review room", correction.comment)
        assertEquals(120, correction.occurredAt)
        assertEquals(ReviewStatus.FINISHED, reviews.getReview(id)!!.status)
    }

    @Test
    fun existingMovementsAreNeverChanged() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        reviews.setCount(boltLine(id).id, 1_000, now = 110)
        reviews.setUnitResult(unitLine(id, "d1").id, ReviewResult.ELSEWHERE, "shelf", now = 110)
        reviews.setUnitResult(unitLine(id, "d2").id, ReviewResult.MISSING, null, now = 110)
        val before = allMovements()

        reviews.finishReview(id, emptyMap(), comment = "c", now = 120, newId = newId)

        val after = allMovements()
        assertTrue(after.containsAll(before))
        assertEquals(before.size + 4, after.size)
    }

    @Test
    fun unitFoundElsewhereIsATransferAndUpdatesTheLocationCache() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        reviews.setUnitResult(unitLine(id, "d1").id, ReviewResult.ELSEWHERE, "shelf", now = 110)
        val before = allMovements()

        reviews.finishReview(id, emptyMap(), comment = "c", now = 120, newId = newId)

        val added = (allMovements() - before.toSet()).sortedBy { it.quantityMilli }
        assertEquals(listOf(-UNIT_MILLI to "bin", UNIT_MILLI to "shelf"), added.map { it.quantityMilli to it.locationId })
        assertTrue(added.all { it.reason == MovementReason.TRANSFER && it.itemId == "d1" })
        assertEquals("shelf", unit("d1").locationId)
    }

    @Test
    fun missingUnitIsLostOrKept() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        val d1 = unitLine(id, "d1").id
        val d2 = unitLine(id, "d2").id
        reviews.setUnitResult(d1, ReviewResult.MISSING, null, now = 110)
        reviews.setUnitResult(d2, ReviewResult.MISSING, null, now = 110)

        reviews.finishReview(id, mapOf(d1 to MissingResolution.LOSE, d2 to MissingResolution.KEEP), comment = "c", now = 120, newId = newId)

        assertEquals(ItemStatus.LOST, unit("d1").status)
        assertNull(unit("d1").locationId)
        assertTrue(allMovements().any { it.itemId == "d1" && it.reason == MovementReason.LOSS && it.quantityMilli == -UNIT_MILLI })
        assertEquals(ItemStatus.AVAILABLE, unit("d2").status)
        assertEquals("shelf", unit("d2").locationId)
    }

    @Test
    fun lostUnitFoundHereComesBack() = runBlocking {
        actions.applyUnitAction("d3", UnitAction.LOSE, null, null, newId(), null, now = 50)
        val id = reviews.startReview("room", now = 100, newId = newId)

        val line = reviews.addFoundUnit(id, "d3", "bin", now = 110, newId = newId)
        assertEquals(0, lines(id).single { it.id == line }.expectedMilli)
        reviews.finishReview(id, emptyMap(), comment = "c", now = 120, newId = newId)

        assertEquals(ItemStatus.AVAILABLE, unit("d3").status)
        assertEquals("bin", unit("d3").locationId)
        assertTrue(allMovements().any { it.itemId == "d3" && it.reason == MovementReason.INVENTORY && it.locationId == "bin" })
    }

    @Test
    fun resettingAnAddedUnitRemovesItsLine() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        val line = reviews.addFoundUnit(id, "d3", "bin", now = 110, newId = newId)

        reviews.resetLine(line, now = 120)

        assertTrue(lines(id).none { it.itemId == "d3" })
        assertTrue(db.backupDao().snapshot().reviewLines.single { it.id == line }.deletedAt != null)
    }

    @Test
    fun addingAnExpectedUnitAtAnotherLocationMarksItElsewhere() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)

        reviews.addFoundUnit(id, "d1", "shelf", now = 110, newId = newId)

        val line = unitLine(id, "d1")
        assertEquals(ReviewResult.ELSEWHERE, line.result)
        assertEquals("shelf", line.foundLocationId)
    }

    @Test
    fun closedReviewRefusesWritesAndChangesNothing() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        reviews.setCount(boltLine(id).id, 1_000, now = 110)
        reviews.cancelReview(id, now = 120)
        val before = allMovements()

        assertFailsWith<ReviewClosedException> { reviews.finishReview(id, emptyMap(), "c", 130, newId) }
        assertFailsWith<ReviewClosedException> { reviews.setCount(boltLine(id).id, 2_000, now = 130) }

        assertEquals(before, allMovements())
        assertEquals(ReviewStatus.CANCELLED, reviews.getReview(id)!!.status)
        // A new review can start once the other is closed
        assertTrue(reviews.startReview("room", now = 140, newId = newId) != id)
    }

    @Test
    fun lastReviewedIsTheLatestFinishedReview() = runBlocking {
        val id = reviews.startReview("shelf", now = 100, newId = newId)
        reviews.finishReview(id, emptyMap(), comment = "c", now = 150, newId = newId)

        assertEquals(listOf("shelf" to 150L), reviews.observeLastReviewed().first().map { it.locationId to it.finishedAt })
    }

    @Test
    fun summaryCountsCheckedAndDiscrepancies() = runBlocking {
        val id = reviews.startReview("room", now = 100, newId = newId)
        reviews.markCorrect(boltLine(id).id, now = 110)
        reviews.setUnitResult(unitLine(id, "d1").id, ReviewResult.MISSING, null, now = 110)
        reviews.setFlag(unitLine(id, "d2").id, true, "  serial unreadable ", now = 110)

        val row = reviews.observeReviews().first().single()

        assertEquals(3, row.total)
        assertEquals(2, row.checked)
        assertEquals(1, row.discrepancies)
        assertEquals(1, row.flagged)
        assertEquals("serial unreadable", unitLine(id, "d2").note)
    }
}

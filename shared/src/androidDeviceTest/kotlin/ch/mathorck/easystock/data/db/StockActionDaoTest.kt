package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StockActionDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var actions: StockActionDao
    private lateinit var detail: ArticleDetailDao
    private var ids = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        actions = db.stockActionDao()
        detail = db.articleDetailDao()
        val stock = db.stockDao()
        stock.insertLocation(Location(id = "shelf", name = "Shelf", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "van", name = "Van", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "old", name = "Old", createdAt = 0, updatedAt = 0, deletedAt = 1))
        db.articleDao().insert(Article(id = "bolt", name = "Bolt", createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "drill", name = "Drill", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private fun id() = "id${ids++}"

    private fun movement(quantity: Long, location: String = "shelf", reason: MovementReason, article: String = "bolt", at: Long = 10) =
        StockMovement(
            id = id(), articleId = article, locationId = location, quantityMilli = quantity, reason = reason,
            occurredAt = at, createdAt = at,
        )

    private suspend fun stockAt(location: String, article: String = "bolt") = actions.stockAt(article, location)
    private suspend fun movementCount() = movements("bolt").size + movements("drill").size
    private suspend fun movements(article: String) = detail.observeMovements(article).first().map { it.movement }
    private suspend fun item(id: String) = detail.observeItems("drill").first().single { it.id == id }

    // ---------- Quantity ----------

    @Test
    fun addAndRemoveSumAtTheLocation() = runBlocking {
        actions.adjust(movement(10_000, reason = MovementReason.PURCHASE))
        actions.adjust(movement(-2_500, reason = MovementReason.CONSUMPTION))
        actions.adjust(movement(1_000, location = "van", reason = MovementReason.INVENTORY))

        assertEquals(7_500, stockAt("shelf"))
        assertEquals(1_000, stockAt("van"))
    }

    @Test
    fun removingMoreThanTheStockAtTheLocationFails() = runBlocking {
        actions.adjust(movement(5_000, reason = MovementReason.PURCHASE))
        actions.adjust(movement(5_000, location = "van", reason = MovementReason.PURCHASE))

        val error = assertFailsWith<InsufficientStockException> {
            actions.adjust(movement(-5_001, reason = MovementReason.CONSUMPTION))
        }
        assertEquals(5_000, error.availableMilli)
        assertEquals(2, movementCount())
        actions.adjust(movement(-5_000, reason = MovementReason.LOSS))
        assertEquals(0, stockAt("shelf"))
    }

    @Test
    fun reasonMustMatchTheDirection() = runBlocking {
        assertFailsWith<IllegalArgumentException> { actions.adjust(movement(1_000, reason = MovementReason.CONSUMPTION)) }
        assertFailsWith<IllegalArgumentException> { actions.adjust(movement(-1_000, reason = MovementReason.PURCHASE)) }
        assertFailsWith<IllegalArgumentException> { actions.adjust(movement(1_000, reason = MovementReason.TRANSFER)) }
        assertEquals(0, movementCount())
    }

    @Test
    fun addCreatesTheNewLocationInTheSameTransaction() = runBlocking {
        val garage = Location(id = "garage", name = "Garage", parentId = "shelf", createdAt = 10, updatedAt = 10)
        actions.adjust(movement(3_000, location = "garage", reason = MovementReason.PURCHASE), garage)

        assertEquals(3_000, stockAt("garage"))
        assertEquals("shelf", detail.observeAllLocations().first().single { it.id == "garage" }.parentId)
    }

    @Test
    fun addingToADeletedLocationFails() = runBlocking {
        assertFailsWith<StockTargetNotFoundException> { actions.adjust(movement(1_000, location = "old", reason = MovementReason.PURCHASE)) }
        assertEquals(0, movementCount())
    }

    @Test
    fun transferIsTwoRowsAndKeepsTheTotal() = runBlocking {
        actions.adjust(movement(10_000, reason = MovementReason.PURCHASE, at = 1))
        actions.transfer(
            out = movement(-4_000, location = "shelf", reason = MovementReason.TRANSFER),
            into = movement(4_000, location = "van", reason = MovementReason.TRANSFER),
        )

        assertEquals(6_000, stockAt("shelf"))
        assertEquals(4_000, stockAt("van"))
        val transfer = movements("bolt").filter { it.reason == MovementReason.TRANSFER }
        assertEquals(2, transfer.size)
        assertEquals(1, transfer.map { it.occurredAt }.distinct().size)
        assertEquals(0, transfer.sumOf { it.quantityMilli })
    }

    @Test
    fun transferCannotMoveMoreThanTheStockAndSavesNothing() = runBlocking {
        actions.adjust(movement(1_000, reason = MovementReason.PURCHASE))
        assertFailsWith<InsufficientStockException> {
            actions.transfer(
                out = movement(-1_001, location = "shelf", reason = MovementReason.TRANSFER),
                into = movement(1_001, location = "van", reason = MovementReason.TRANSFER),
            )
        }
        assertEquals(1, movementCount())
        assertEquals(1_000, stockAt("shelf"))
    }

    @Test
    fun transferToANewLocationRollsBackOnError() = runBlocking {
        val garage = Location(id = "garage", name = "Garage", createdAt = 10, updatedAt = 10)
        assertFailsWith<InsufficientStockException> {
            actions.transfer(
                out = movement(-1_000, location = "shelf", reason = MovementReason.TRANSFER),
                into = movement(1_000, location = "garage", reason = MovementReason.TRANSFER),
                newLocation = garage,
            )
        }
        assertEquals(0, detail.observeAllLocations().first().count { it.id == "garage" })
    }

    @Test
    fun transferOutOfADeletedLocationIsAllowed() = runBlocking {
        db.stockDao().insertMovement(movement(2_000, location = "old", reason = MovementReason.PURCHASE))
        actions.transfer(
            out = movement(-2_000, location = "old", reason = MovementReason.TRANSFER),
            into = movement(2_000, location = "shelf", reason = MovementReason.TRANSFER),
        )
        assertEquals(0, stockAt("old"))
        assertEquals(2_000, stockAt("shelf"))
    }

    // ---------- Serial units ----------

    private suspend fun addUnit(itemId: String, serial: String?, location: String = "shelf") {
        val item = Item(id = itemId, articleId = "drill", serialNumber = serial, locationId = location, createdAt = 1, updatedAt = 1)
        actions.addItem(item, movement(1_000, location = location, reason = MovementReason.PURCHASE, article = "drill", at = 1).copy(itemId = itemId))
    }

    @Test
    fun addUnitInsertsTheItemAndAPlusOneMovement() = runBlocking {
        addUnit("u1", "SN-1")
        addUnit("u2", null)

        assertEquals(2_000, stockAt("shelf", "drill"))
        assertEquals("shelf", item("u1").locationId)
        assertEquals(listOf("u1", "u2"), movements("drill").mapNotNull { it.itemId }.sorted())
    }

    @Test
    fun duplicateSerialIgnoringCaseFails() = runBlocking {
        addUnit("u1", "SN-1")
        assertFailsWith<DuplicateSerialException> { addUnit("u2", "sn-1") }
        assertEquals(1, detail.observeItems("drill").first().size)
        assertEquals(1, movementCount())
    }

    @Test
    fun moveUnitWritesTwoRowsAndUpdatesTheCache() = runBlocking {
        addUnit("u1", "SN-1")
        actions.moveItem("u1", "van", null, id(), id(), comment = null, now = 20)

        assertEquals("van", item("u1").locationId)
        assertEquals(0, stockAt("shelf", "drill"))
        assertEquals(1_000, stockAt("van", "drill"))
        val transfer = movements("drill").filter { it.reason == MovementReason.TRANSFER }
        assertEquals(listOf(-1_000L, 1_000L), transfer.map { it.quantityMilli }.sorted())
        assertEquals(setOf("u1"), transfer.mapNotNull { it.itemId }.toSet())
        assertEquals("van", detail.observeLastItemLocations("drill").first().single().locationId)
    }

    @Test
    fun moveUnitToANewLocation() = runBlocking {
        addUnit("u1", null)
        val garage = Location(id = "garage", name = "Garage", createdAt = 20, updatedAt = 20)
        actions.moveItem("u1", "garage", garage, id(), id(), comment = "c", now = 20)
        assertEquals("garage", item("u1").locationId)
    }

    @Test
    fun lendTakesTheUnitOutAndReturnBringsItBack() = runBlocking {
        addUnit("u1", "SN-1")
        actions.applyUnitAction("u1", UnitAction.LEND, null, null, id(), comment = null, now = 20)

        assertEquals(ItemStatus.LENT, item("u1").status)
        assertNull(item("u1").locationId)
        assertEquals(0, stockAt("shelf", "drill"))
        assertEquals("shelf", detail.observeLastItemLocations("drill").first().single().locationId)
        assertFailsWith<InvalidItemStatusException> { actions.moveItem("u1", "van", null, id(), id(), null, 21) }

        actions.applyUnitAction("u1", UnitAction.RETURN, "van", null, id(), comment = null, now = 30)
        assertEquals(ItemStatus.AVAILABLE, item("u1").status)
        assertEquals("van", item("u1").locationId)
        assertEquals(1_000, stockAt("van", "drill"))
    }

    @Test
    fun loseFromAvailableAddsALossAndFromLentAddsNothing() = runBlocking {
        addUnit("u1", "SN-1")
        addUnit("u2", "SN-2")
        actions.applyUnitAction("u1", UnitAction.LOSE, null, null, id(), comment = null, now = 20)
        assertEquals(ItemStatus.LOST, item("u1").status)
        assertNull(item("u1").locationId)
        assertEquals(1_000, stockAt("shelf", "drill"))

        actions.applyUnitAction("u2", UnitAction.LEND, null, null, id(), comment = null, now = 21)
        val before = movementCount()
        actions.applyUnitAction("u2", UnitAction.LOSE, null, null, id(), comment = null, now = 22)
        assertEquals(before, movementCount())
        assertEquals(ItemStatus.LOST, item("u2").status)
        assertEquals(0, stockAt("shelf", "drill"))

        actions.applyUnitAction("u1", UnitAction.FOUND, "van", null, id(), comment = null, now = 23)
        assertEquals("van", item("u1").locationId)
        assertEquals(1_000, stockAt("van", "drill"))
    }

    @Test
    fun repairKeepsTheUnitInStock() = runBlocking {
        addUnit("u1", null)
        val before = movementCount()
        actions.applyUnitAction("u1", UnitAction.REPAIR, null, null, id(), comment = null, now = 20)
        assertEquals(ItemStatus.REPAIR, item("u1").status)
        assertEquals("shelf", item("u1").locationId)
        assertEquals(before, movementCount())
        assertFailsWith<InvalidItemStatusException> {
            actions.applyUnitAction("u1", UnitAction.LEND, null, null, id(), comment = null, now = 21)
        }
        actions.applyUnitAction("u1", UnitAction.END_REPAIR, null, null, id(), comment = null, now = 22)
        assertEquals(ItemStatus.AVAILABLE, item("u1").status)
    }

    @Test
    fun quantityMethodsRejectSerialArticles() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            actions.adjust(movement(1_000, reason = MovementReason.PURCHASE, article = "drill"))
        }
        assertEquals(0, movementCount())
    }
}

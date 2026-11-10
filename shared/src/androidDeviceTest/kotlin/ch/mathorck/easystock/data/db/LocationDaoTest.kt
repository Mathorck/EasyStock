package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.viewmodels.LocationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LocationDao
    private var ids = 0

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.locationDao()
        db.articleDao().insert(Article(id = "bolt", name = "Bolt", createdAt = 0, updatedAt = 0))
        // room > shelf > bin, room > cabinet, garage
        dao.createLocation("room", "Room", null, 1)
        dao.createLocation("shelf", "Shelf", "room", 1)
        dao.createLocation("bin", "Bin", "shelf", 1)
        dao.createLocation("cabinet", "Cabinet", "room", 1)
        dao.createLocation("garage", "Garage", null, 1)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun active() = dao.observeLocations().first()
    private suspend fun all() = db.articleDetailDao().observeAllLocations().first().associateBy { it.id }

    private suspend fun refused(block: suspend () -> Unit): LocationError =
        assertFailsWith<LocationException> { block() }.error

    private suspend fun addMovement(location: String, quantity: Long, article: String = "bolt", itemId: String? = null) {
        db.stockDao().insertMovement(
            StockMovement(
                id = "m${ids++}", articleId = article, itemId = itemId, locationId = location, quantityMilli = quantity,
                reason = MovementReason.PURCHASE, occurredAt = 5, createdAt = 5,
            ),
        )
    }

    @Test
    fun createTrimsTheNameAndSetsTimestamps() = runBlocking<Unit> {
        val created = dao.createLocation("new", "  Attic ", "room", 42)
        assertEquals("Attic", created.name)
        assertEquals("room", created.parentId)
        assertEquals(42, created.createdAt)
        assertEquals(42, created.updatedAt)
        assertNull(created.deletedAt)
    }

    @Test
    fun createRefusesBadNamesAndParents() = runBlocking<Unit> {
        assertEquals(LocationError.REQUIRED, refused { dao.createLocation("x", "  ", null, 2) })
        assertEquals(LocationError.TOO_LONG, refused { dao.createLocation("x", "a".repeat(101), null, 2) })
        assertEquals(LocationError.DUPLICATE, refused { dao.createLocation("x", "ROOM", null, 2) })
        assertEquals(LocationError.PARENT_NOT_FOUND, refused { dao.createLocation("x", "X", "nope", 2) })
        // The same name is fine under another parent
        dao.createLocation("x", "Shelf", "garage", 2)
        assertEquals(6, active().size)
    }

    @Test
    fun renameKeepsParentAndBumpsUpdatedAt() = runBlocking<Unit> {
        dao.updateLocation("shelf", "Big shelf", "room", 9)
        val shelf = all().getValue("shelf")
        assertEquals("Big shelf", shelf.name)
        assertEquals("room", shelf.parentId)
        assertEquals(9, shelf.updatedAt)
        assertEquals(1, shelf.createdAt)
        assertEquals(LocationError.DUPLICATE, refused { dao.updateLocation("shelf", "cabinet", "room", 10) })
        assertEquals(9, all().getValue("shelf").updatedAt)
    }

    @Test
    fun moveChangesTheParent() = runBlocking<Unit> {
        dao.updateLocation("bin", "Bin", "garage", 7)
        assertEquals("garage", all().getValue("bin").parentId)
        dao.updateLocation("bin", "Bin", null, 8)
        assertNull(all().getValue("bin").parentId)
        assertEquals(8, all().getValue("bin").updatedAt)
    }

    @Test
    fun moveRefusesSiblingNameClashAtTheTarget() = runBlocking<Unit> {
        dao.createLocation("garage-shelf", "shelf", "garage", 2)
        assertEquals(LocationError.DUPLICATE, refused { dao.updateLocation("shelf", "Shelf", "garage", 3) })
        assertEquals("room", all().getValue("shelf").parentId)
    }

    @Test
    fun moveRefusesCycles() = runBlocking<Unit> {
        assertEquals(LocationError.CYCLE, refused { dao.updateLocation("room", "Room", "room", 5) })
        assertEquals(LocationError.CYCLE, refused { dao.updateLocation("room", "Room", "bin", 5) })
        assertEquals(LocationError.CYCLE, refused { dao.updateLocation("shelf", "Shelf", "bin", 5) })
        assertNull(all().getValue("room").parentId)
        assertEquals(1, all().getValue("room").updatedAt)
    }

    @Test
    fun deleteIsBlockedByStockInTheLocation() = runBlocking<Unit> {
        addMovement("bin", 3000)
        assertEquals(LocationError.HAS_STOCK, refused { dao.softDeleteLocation("bin", 20) })
        // A descendant holding stock blocks its ancestors too
        assertEquals(LocationError.HAS_STOCK, refused { dao.softDeleteLocation("room", 20) })
        assertTrue(dao.subtreeHoldsStock("room"))
        assertEquals(5, active().size)
        assertTrue(all().values.all { it.deletedAt == null })
    }

    @Test
    fun deleteIsBlockedByAnItemCachedThere() = runBlocking<Unit> {
        db.articleDao().insert(Article(id = "drill", name = "Drill", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
        val unit = Item(id = "d1", articleId = "drill", locationId = "cabinet", createdAt = 0, updatedAt = 0)
        db.stockActionDao().addItem(
            unit,
            StockMovement(
                id = "m-add", articleId = "drill", itemId = "d1", locationId = "cabinet", quantityMilli = 1000,
                reason = MovementReason.PURCHASE, occurredAt = 5, createdAt = 5,
            ),
        )
        // The movements now sum to 0 there: only the cached item location is left to block the delete
        addMovement("cabinet", -1000, article = "drill", itemId = "d1")
        assertEquals(LocationError.HAS_STOCK, refused { dao.softDeleteLocation("room", 20) })
    }

    @Test
    fun deleteIsAllowedWhenMovementsSumToZero() = runBlocking<Unit> {
        addMovement("bin", 3000)
        addMovement("bin", -3000)
        assertEquals(1, dao.softDeleteLocation("bin", 20))
        assertEquals(20, all().getValue("bin").deletedAt)
        // The movements stay untouched
        assertEquals(2, db.articleDetailDao().observeMovements("bolt").first().size)
    }

    @Test
    fun deleteSoftDeletesTheWholeSubtreeOnly() = runBlocking<Unit> {
        assertEquals(4, dao.softDeleteLocation("room", 30))
        val locations = all()
        listOf("room", "shelf", "bin", "cabinet").forEach {
            assertEquals(30, locations.getValue(it).deletedAt, it)
            assertEquals(30, locations.getValue(it).updatedAt, it)
        }
        assertNull(locations.getValue("garage").deletedAt)
        assertEquals(listOf("garage"), active().map { it.id })
        assertEquals(LocationError.NOT_FOUND, refused { dao.softDeleteLocation("room", 31) })
    }

    @Test
    fun deletedLocationFreesItsNameAndIsNotAParent() = runBlocking<Unit> {
        dao.softDeleteLocation("garage", 30)
        dao.createLocation("garage2", "Garage", null, 31)
        assertEquals(LocationError.PARENT_NOT_FOUND, refused { dao.createLocation("y", "Y", "garage", 32) })
        assertNotNull(all()["garage"])
    }

    @Test
    fun articleCountsPerLocation() = runBlocking<Unit> {
        addMovement("bin", 3000)
        addMovement("shelf", 1000)
        addMovement("shelf", -1000)
        val counts = dao.observeArticleCounts().first().associate { it.locationId to it.articleCount }
        assertEquals(mapOf("bin" to 1), counts)
    }
}

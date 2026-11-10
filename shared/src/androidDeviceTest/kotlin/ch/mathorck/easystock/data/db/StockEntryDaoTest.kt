package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StockEntryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var articles: ArticleDao
    private lateinit var entries: StockEntryDao

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        articles = db.articleDao()
        entries = db.stockEntryDao()
        db.stockDao().insertLocation(Location(id = "shelf", name = "Shelf", createdAt = 0, updatedAt = 0))
        articles.insert(Article(id = "bolt", sku = "BLT-8", name = "Bolt", createdAt = 0, updatedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private fun movement(id: String, articleId: String, locationId: String, quantityMilli: Long = 1_500) = StockMovement(
        id = id,
        articleId = articleId,
        locationId = locationId,
        quantityMilli = quantityMilli,
        reason = MovementReason.PURCHASE,
        occurredAt = 1,
        createdAt = 1,
    )

    private fun newEntry(sku: String) = NewStockEntry(
        newCategory = Category(id = "cat", name = "Hardware", createdAt = 1, updatedAt = 1),
        newArticle = Article(id = "nail", sku = sku, name = "Nail", categoryId = "cat", createdAt = 1, updatedAt = 1),
        newLocation = Location(id = "garage", name = "Garage", parentId = "shelf", createdAt = 1, updatedAt = 1),
        movement = movement("m1", articleId = "nail", locationId = "garage"),
    )

    private suspend fun stockOf(id: String) = articles.observeStock().first().single { it.article.id == id }.stockMilli

    @Test
    fun newArticleAndLocationAreSavedWithOneMovement() = runBlocking {
        entries.save(newEntry("NL-1"))

        assertEquals(1, entries.countMovements())
        assertEquals(2, entries.countLocations())
        assertEquals(1, entries.countCategories())
        assertEquals("cat", articles.getById("nail")?.categoryId)
        assertEquals(1_500, stockOf("nail"))
    }

    @Test
    fun entryOnExistingRowsAddsToTheStock() = runBlocking {
        entries.save(NewStockEntry(movement = movement("m1", "bolt", "shelf", 2_000)))
        entries.save(NewStockEntry(movement = movement("m2", "bolt", "shelf", 500)))

        assertEquals(2_500, stockOf("bolt"))
        assertEquals(2, entries.countMovements())
    }

    @Test
    fun duplicateSkuSavesNothing() = runBlocking {
        assertFailsWith<DuplicateSkuException> { entries.save(newEntry("blt-8")) }

        assertEquals(0, entries.countMovements())
        assertEquals(1, entries.countLocations())
        assertEquals(0, entries.countCategories())
        assertEquals(0, stockOf("bolt"))
    }

    @Test
    fun skuOfADeletedArticleIsStillTaken() = runBlocking {
        articles.softDelete("bolt", now = 1)

        assertFailsWith<DuplicateSkuException> { entries.save(newEntry("BLT-8")) }
        assertEquals(0, entries.countMovements())
    }

    @Test
    fun activeCategoryWithTheSameNameIsReused() = runBlocking {
        db.categoryDao().insert(Category(id = "existing", name = "hardware", createdAt = 0, updatedAt = 0))

        entries.save(newEntry("NL-1"))

        assertEquals(1, entries.countCategories())
        assertEquals("existing", articles.getById("nail")?.categoryId)
    }

    @Test
    fun nonPositiveQuantityIsRejected() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            entries.save(NewStockEntry(movement = movement("m1", "bolt", "shelf", 0)))
        }
        assertEquals(0, entries.countMovements())
    }
}

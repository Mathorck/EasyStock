package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.viewmodels.filterStock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class StockFilterQueryTest {
    private lateinit var db: AppDatabase
    private lateinit var articles: ArticleDao
    private lateinit var stock: StockDao
    private var movementCount = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        articles = db.articleDao()
        stock = db.stockDao()
        // room > shelf > bin, and a separate van
        location("room")
        location("shelf", parent = "room")
        location("bin", parent = "shelf")
        location("van")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun location(id: String, parent: String? = null, deletedAt: Long? = null) =
        stock.insertLocation(Location(id = id, name = id, parentId = parent, createdAt = 0, updatedAt = 0, deletedAt = deletedAt))

    private suspend fun article(id: String, categoryId: String? = null, archived: Boolean = false) =
        articles.insert(
            Article(id = id, sku = id, name = id, categoryId = categoryId, archived = archived, createdAt = 0, updatedAt = 0),
        )

    private suspend fun move(articleId: String, quantityMilli: Long, location: String) =
        stock.insertMovement(
            StockMovement(
                id = "m${movementCount++}",
                articleId = articleId,
                locationId = location,
                quantityMilli = quantityMilli,
                reason = MovementReason.INVENTORY,
                occurredAt = 0,
                createdAt = 0,
            ),
        )

    private suspend fun stockAt(location: String) =
        articles.observeStockAt(location).first().associate { it.article.id to it.stockMilli }

    @Test
    fun sumsOnlyThatLocation() = runBlocking {
        article("a1")
        move("a1", 5_000, "van")
        move("a1", 3_000, "bin")

        assertEquals(mapOf("a1" to 5_000L), stockAt("van"))
    }

    @Test
    fun includesChildLocationsRecursively() = runBlocking {
        article("a1")
        move("a1", 1_000, "room")
        move("a1", 2_000, "shelf")
        move("a1", 4_000, "bin")
        move("a1", 8_000, "van")

        assertEquals(mapOf("a1" to 7_000L), stockAt("room"))
        assertEquals(mapOf("a1" to 6_000L), stockAt("shelf"))
        assertEquals(mapOf("a1" to 4_000L), stockAt("bin"))
    }

    @Test
    fun excludesArticlesWithoutMovementsThere() = runBlocking {
        article("a1")
        article("a2")
        article("a3")
        move("a1", 1_000, "shelf")
        move("a2", 1_000, "van")

        assertEquals(mapOf("a1" to 1_000L), stockAt("room"))
    }

    @Test
    fun transferOutLeavesArticleListedWithZero() = runBlocking {
        article("a1")
        move("a1", 2_000, "bin")
        move("a1", -2_000, "bin")
        move("a1", 2_000, "van")

        assertEquals(mapOf("a1" to 0L), stockAt("shelf"))
        assertEquals(mapOf("a1" to 2_000L), stockAt("van"))
    }

    @Test
    fun includesSoftDeletedChildLocations() = runBlocking {
        location("old-bin", parent = "shelf", deletedAt = 1)
        article("a1")
        move("a1", 3_000, "old-bin")

        assertEquals(mapOf("a1" to 3_000L), stockAt("room"))
    }

    @Test
    fun hidesDeletedAndArchivedArticles() = runBlocking {
        article("active")
        article("old", archived = true)
        article("gone")
        listOf("active", "old", "gone").forEach { move(it, 1_000, "shelf") }
        articles.softDelete("gone", now = 1)

        assertEquals(listOf("active"), articles.observeStockAt("room").first().map { it.article.id })
    }

    @Test
    fun categoryFilterOnLocationStock() = runBlocking {
        db.categoryDao().insert(Category(id = "hw", name = "Hardware", createdAt = 0, updatedAt = 0))
        db.categoryDao().insert(Category(id = "tape", name = "Tape", createdAt = 0, updatedAt = 0))
        article("screw", categoryId = "hw")
        article("duct", categoryId = "tape")
        article("bolt", categoryId = "hw")
        move("screw", 1_000, "shelf")
        move("duct", 1_000, "shelf")
        move("bolt", 1_000, "van")

        val atRoom = articles.observeStockAt("room").first()
        assertEquals(listOf("screw"), atRoom.filterStock("", "hw", false).map { it.article.id })
        val all = articles.observeStock().first()
        assertEquals(listOf("bolt", "screw"), all.filterStock("", "hw", false).map { it.article.id })
    }
}

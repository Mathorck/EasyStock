package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class StockQueryTest {
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
        stock.insertLocation(Location(id = "shelf", name = "Shelf", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "van", name = "Van", createdAt = 0, updatedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun article(id: String, name: String = id, archived: Boolean = false) =
        articles.insert(Article(id = id, sku = id, name = name, archived = archived, createdAt = 0, updatedAt = 0))

    private suspend fun move(articleId: String, quantityMilli: Long, location: String = "shelf") =
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

    private suspend fun stockOf(id: String) = articles.observeStock().first().single { it.article.id == id }.stockMilli

    @Test
    fun articleWithoutMovementsHasZeroStock() = runBlocking {
        article("a1")

        assertEquals(0, stockOf("a1"))
    }

    @Test
    fun stockIsTheSumOfInAndOut() = runBlocking {
        article("a1")
        move("a1", 10_000)
        move("a1", -2_500)

        assertEquals(7_500, stockOf("a1"))
    }

    @Test
    fun transferKeepsTheTotal() = runBlocking {
        article("a1")
        move("a1", 5_000, "shelf")
        move("a1", -2_000, "shelf")
        move("a1", 2_000, "van")

        assertEquals(5_000, stockOf("a1"))
    }

    @Test
    fun movementsOfOtherArticlesAreNotCounted() = runBlocking {
        article("a1")
        article("a2")
        move("a1", 1_000)
        move("a2", 3_000)

        assertEquals(1_000, stockOf("a1"))
        assertEquals(3_000, stockOf("a2"))
    }

    @Test
    fun deletedAndArchivedArticlesAreHidden() = runBlocking {
        article("active")
        article("old", archived = true)
        article("gone")
        articles.softDelete("gone", now = 1)

        assertEquals(listOf("active"), articles.observeStock().first().map { it.article.id })
    }

    @Test
    fun listIsSortedByName() = runBlocking {
        article("a1", name = "Bolt")
        article("a2", name = "Anchor")

        assertEquals(listOf("Anchor", "Bolt"), articles.observeStock().first().map { it.article.name })
    }
}

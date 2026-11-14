package ch.mathorck.easystock.data.db

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The article COUNT of the Stock page and the movement LIMIT of the article history, on an in-memory database. */
class StockCountAndHistoryTest {
    private lateinit var db: AppDatabase
    private var movementCount = 0

    @BeforeTest
    fun setUp() = runBlocking {
        db = buildAppDatabase(
            // TRUNCATE: no WAL, so Room never asks the (absent) Android context for a service
            Room.inMemoryDatabaseBuilder<AppDatabase>(ContextWrapper(null)).setJournalMode(RoomDatabase.JournalMode.TRUNCATE),
        )
        db.stockDao().insertLocation(Location(id = "shelf", name = "Shelf", createdAt = 0, updatedAt = 0))
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun article(id: String, archived: Boolean = false, deletedAt: Long? = null) =
        db.articleDao().insert(Article(id = id, name = id, archived = archived, deletedAt = deletedAt, createdAt = 0, updatedAt = 0))

    private suspend fun move(articleId: String, quantityMilli: Long, occurredAt: Long = 0) =
        db.stockDao().insertMovement(
            StockMovement(
                id = "m${movementCount++}",
                articleId = articleId,
                locationId = "shelf",
                quantityMilli = quantityMilli,
                reason = MovementReason.INVENTORY,
                occurredAt = occurredAt,
                createdAt = occurredAt,
            ),
        )

    @Test
    fun activeCountMatchesTheStockList() = runBlocking {
        article("with-stock")
        move("with-stock", 5_000)
        move("with-stock", -1_000)
        article("without-movements")
        article("used-up")
        move("used-up", 1_000)
        move("used-up", -1_000)
        article("archived", archived = true)
        move("archived", 2_000)
        article("deleted", deletedAt = 10)
        move("deleted", 3_000)

        val articles = db.articleDao()
        assertEquals(3, articles.observeActiveCount().first())
        assertEquals(articles.observeStock().first().size, articles.observeActiveCount().first())
    }

    @Test
    fun activeCountIsZeroWithoutArticles() = runBlocking {
        assertEquals(0, db.articleDao().observeActiveCount().first())
    }

    @Test
    fun movementsAreLimitedToTheNewest() = runBlocking {
        article("a1")
        (1..5).forEach { move("a1", it * 1_000L, occurredAt = it * 100L) }
        article("other")
        move("other", 1_000, occurredAt = 1_000)

        val detail = db.articleDetailDao()
        val newest = detail.observeMovements("a1", 3).first()
        assertEquals(listOf(500L, 400L, 300L), newest.map { it.movement.occurredAt })
        assertEquals(5, detail.observeMovements("a1", 10).first().size)
    }
}

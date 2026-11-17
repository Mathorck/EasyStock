package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.DateAlertNotice
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class DateAlertDaoTest {
    private lateinit var db: AppDatabase
    private val values = JsonObject(mapOf("expiry" to JsonPrimitive("2026-01-01")))

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addItem(item: Item) = db.stockActionDao().addItem(
        item,
        StockMovement(
            id = "m-${item.id}", articleId = item.articleId, itemId = item.id, locationId = "room", quantityMilli = 1000,
            reason = MovementReason.PURCHASE, occurredAt = 5, createdAt = 5,
        ),
    )

    @Test
    fun readsOnlyLiveRowsWithValues() = runBlocking<Unit> {
        db.locationDao().createLocation("room", "Room", null, 1)
        val articles = db.articleDao()
        articles.insert(Article(id = "a1", name = "A1", tracking = Tracking.SERIAL, attributes = values, createdAt = 0, updatedAt = 0))
        articles.insert(Article(id = "a2", name = "A2", createdAt = 0, updatedAt = 0))
        articles.insert(Article(id = "a3", name = "A3", attributes = values, createdAt = 0, updatedAt = 0, deletedAt = 1))
        addItem(Item(id = "i1", articleId = "a1", locationId = "room", attributes = values, createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i2", articleId = "a1", locationId = "room", createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i3", articleId = "a1", locationId = "room", attributes = values, createdAt = 0, updatedAt = 0, deletedAt = 1))

        assertEquals(listOf("a1"), db.dateAlertDao().observeArticles().first().map { it.id })
        assertEquals(listOf("i1"), db.dateAlertDao().observeItems().first().map { it.id })
    }

    @Test
    fun allNoticesAreListed() = runBlocking<Unit> {
        db.dateAlertNoticeDao().markNotified(DateAlertNotice("a1", "expiry", "2026-01-01", 5))
        assertEquals(listOf("a1"), db.dateAlertNoticeDao().all().map { it.targetId })
    }

    @Test
    fun anArticleWithoutValuesIsReadWhenAnItemHasValues() = runBlocking<Unit> {
        db.locationDao().createLocation("room", "Room", null, 1)
        db.articleDao().insert(Article(id = "plain", name = "Plain", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "empty", name = "Empty", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i1", articleId = "plain", locationId = "room", attributes = values, createdAt = 0, updatedAt = 0))
        assertEquals(listOf("plain"), db.dateAlertDao().observeArticles().first().map { it.id })
        assertEquals(listOf("i1"), db.dateAlertDao().observeItems().first().map { it.id })
    }

    @Test
    fun archivedRowsAreLeftOut() = runBlocking<Unit> {
        db.locationDao().createLocation("room", "Room", null, 1)
        db.articleDao().insert(Article(id = "old", name = "Old", tracking = Tracking.SERIAL, attributes = values, archived = true, createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "a1", name = "A1", tracking = Tracking.SERIAL, attributes = values, createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i-old", articleId = "a1", locationId = "room", attributes = values, archived = true, createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i-of-old", articleId = "old", locationId = "room", attributes = values, createdAt = 0, updatedAt = 0))
        assertEquals(listOf("a1"), db.dateAlertDao().observeArticles().first().map { it.id })
        assertEquals(emptyList(), db.dateAlertDao().observeItems().first())
        assertEquals(emptyList(), db.dateAlertDao().observeArticle("old").first())
    }

    @Test
    fun scopedQueriesReadOneArticleAndItsItems() = runBlocking<Unit> {
        db.locationDao().createLocation("room", "Room", null, 1)
        db.articleDao().insert(Article(id = "a1", name = "A1", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "a2", name = "A2", tracking = Tracking.SERIAL, createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i1", articleId = "a1", locationId = "room", attributes = values, createdAt = 0, updatedAt = 0))
        addItem(Item(id = "i2", articleId = "a2", locationId = "room", attributes = values, createdAt = 0, updatedAt = 0))
        assertEquals(listOf("a1"), db.dateAlertDao().observeArticle("a1").first().map { it.id })
        assertEquals(listOf("i1"), db.dateAlertDao().observeItemsOf("a1").first().map { it.id })
    }

    @Test
    fun noticesAreMarkedAndPrunedInBatches() = runBlocking<Unit> {
        val notices = db.dateAlertNoticeDao()
        notices.markAllNotified(listOf(DateAlertNotice("a1", "expiry", "2026-01-01", 5), DateAlertNotice("a2", "expiry", "2026-01-01", 5)))
        assertEquals(2, notices.all().size)
        notices.deleteNotices(setOf(Triple("a1", "expiry", "2026-01-01"), Triple("zz", "x", "y")))
        assertEquals(listOf("a2"), notices.all().map { it.targetId })
    }
}

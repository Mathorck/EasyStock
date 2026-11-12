package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun exec(vararg sql: String) = db.useWriterConnection { c -> sql.forEach { c.execSQL(it) } }

    @Test
    fun standaloneHasNoRole() = runBlocking<Unit> {
        assertNull(db.alertDao().storedRole())
    }

    @Test
    fun pendingAlertsOldestFirstUntilMarked() = runBlocking<Unit> {
        exec(
            "INSERT INTO sync_state (id, server_url, user_id, role) VALUES (1, 'http://nas:8080', 'u1', 'editor')",
            "INSERT INTO low_stock_alert (id, article_id, crossed_at, stock) VALUES ('al2', 'a1', 20, 0)",
            "INSERT INTO low_stock_alert (id, article_id, crossed_at, stock) VALUES ('al1', 'a1', 10, 0)",
        )
        val store = db.alertDao().asAlertStore()
        assertEquals("editor", store.storedRole())
        assertEquals(listOf("al1", "al2"), store.pending().map { it.id })
        store.markNotified(listOf("al1"))
        assertEquals(listOf("al2"), store.pending().map { it.id })
    }

    @Test
    fun articleStockSumsMovementsAndKeepsDeletedArticles() = runBlocking<Unit> {
        exec(
            "INSERT INTO location (id, name, created_at, updated_at) VALUES ('l1', 'Shelf', 1, 1)",
            "INSERT INTO article (id, name, threshold, created_at, updated_at) VALUES ('a1', 'Bolt', 5000, 1, 1)",
            "INSERT INTO article (id, name, created_at, updated_at, deleted_at) VALUES ('a2', 'Nut', 1, 1, 2)",
            "INSERT INTO stock_movement (id, article_id, location_id, quantity, reason, occurred_at, created_at) " +
                "VALUES ('m1', 'a1', 'l1', 6000, 'purchase', 1, 1)",
            "INSERT INTO stock_movement (id, article_id, location_id, quantity, reason, occurred_at, created_at) " +
                "VALUES ('m2', 'a1', 'l1', -2000, 'consumption', 2, 2)",
        )
        val stock = db.alertDao().asAlertStore().articleStock(listOf("a1", "a2", "missing")).associateBy { it.article.id }
        assertEquals(setOf("a1", "a2"), stock.keys)
        assertEquals(4000, stock.getValue("a1").stockMilli)
        assertTrue(stock.getValue("a1").isLowStock)
        assertEquals(0, stock.getValue("a2").stockMilli)
    }
}

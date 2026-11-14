package ch.mathorck.easystock.data.db

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The stock_movement indices of v12: stock sums read only an index, the history needs no sort. Same SQL as the DAOs. */
class StockIndexPlanTest {
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setUp() {
        db = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(ContextWrapper(null)).setJournalMode(RoomDatabase.JournalMode.TRUNCATE),
        )
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun plan(sql: String): List<String> = runBlocking {
        db.useReaderConnection { connection ->
            connection.usePrepared("EXPLAIN QUERY PLAN $sql") { statement ->
                buildList { while (statement.step()) add(statement.getText(3)) }
            }
        }
    }

    private fun movementStep(sql: String) = plan(sql).single { "stock_movement" in it }

    @Test
    fun stockSumsReadOnlyAnIndex() {
        val sums = listOf(
            // ArticleDao.observeStock
            """SELECT article.*, COALESCE(SUM(stock_movement.quantity), 0) AS stock FROM article
               LEFT JOIN stock_movement ON stock_movement.article_id = article.id
               WHERE article.deleted_at IS NULL AND article.archived = 0 GROUP BY article.id ORDER BY article.name""",
            // ArticleDao.observeStockAt
            """WITH RECURSIVE subtree(id) AS (SELECT id FROM location WHERE id = 'x'
               UNION SELECT location.id FROM location JOIN subtree ON location.parent_id = subtree.id)
               SELECT article.*, SUM(stock_movement.quantity) AS stock FROM article
               JOIN stock_movement ON stock_movement.article_id = article.id
               WHERE stock_movement.location_id IN (SELECT id FROM subtree) AND article.deleted_at IS NULL AND article.archived = 0
               GROUP BY article.id ORDER BY article.name""",
            // stockAt (StockActionDao, ReviewDao, SyncDao)
            "SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE article_id = 'a' AND location_id = 'l'",
            // ArticleDetailDao.observeStockByLocation
            "SELECT location_id, SUM(quantity) AS stock FROM stock_movement WHERE article_id = 'a' GROUP BY location_id HAVING SUM(quantity) != 0",
            // LocationDao.observeArticleCounts
            """SELECT location_id, COUNT(*) AS article_count FROM (SELECT location_id, article_id FROM stock_movement
               GROUP BY location_id, article_id HAVING SUM(quantity) <> 0) GROUP BY location_id""",
        )
        sums.forEach { sql ->
            val step = movementStep(sql)
            assertTrue("COVERING INDEX index_stock_movement_" in step, "not index-only: $step\n$sql")
        }
    }

    @Test
    fun movementHistoryNeedsNoSort() {
        // ArticleDetailDao.observeMovements
        val steps = plan(
            """SELECT stock_movement.*, location.name AS location_name, server_user.display_name AS author_name
               FROM stock_movement JOIN location ON location.id = stock_movement.location_id
               LEFT JOIN server_user ON server_user.user_id = stock_movement.created_by
               WHERE stock_movement.article_id = 'a'
               ORDER BY stock_movement.occurred_at DESC, stock_movement.created_at DESC, stock_movement.id DESC LIMIT 50""",
        )
        assertTrue(steps.any { "index_stock_movement_article_id_occurred_at_created_at_id" in it }, steps.joinToString("\n"))
        assertFalse(steps.any { "TEMP B-TREE" in it }, steps.joinToString("\n"))
    }
}

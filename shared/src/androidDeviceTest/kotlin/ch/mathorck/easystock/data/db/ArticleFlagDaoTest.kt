package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleFlag
import ch.mathorck.easystock.data.model.DateAlertNotice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleFlagDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleFlagDao

    private fun flag(id: String, articleId: String = "a1", createdAt: Long = 1_000) =
        ArticleFlag(id = id, articleId = articleId, note = "Broken $id", createdAt = createdAt, updatedAt = createdAt)

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        dao = db.articleFlagDao()
        db.articleDao().insert(Article(id = "a1", sku = "S1", name = "One", createdAt = 1, updatedAt = 1))
        db.articleDao().insert(Article(id = "a2", sku = "S2", name = "Two", createdAt = 1, updatedAt = 1))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertedFlagIsOpenWithoutAuthor() = runBlocking {
        dao.insert(flag("f1"))

        val open = dao.observeOpen("a1").first()
        assertEquals(listOf("f1"), open.map { it.id })
        assertNull(open.single().createdBy)
        assertNull(open.single().resolvedAt)
    }

    @Test
    fun openFlagsAreOrderedOldestFirstAndScopedToTheArticle() = runBlocking {
        dao.insert(flag("f2", createdAt = 2_000))
        dao.insert(flag("f1", createdAt = 1_000))
        dao.insert(flag("f3", articleId = "a2"))

        assertEquals(listOf("f1", "f2"), dao.observeOpen("a1").first().map { it.id })
        assertEquals(listOf("f1", "f3", "f2"), dao.observeAllOpen().first().map { it.id })
    }

    @Test
    fun resolvedFlagLeavesTheOpenListAndIsKept() = runBlocking {
        dao.insert(flag("f1"))

        assertEquals(1, dao.resolve("f1", 5_000))
        assertTrue(dao.observeOpen("a1").first().isEmpty())
        // Already resolved: nothing changes
        assertEquals(0, dao.resolve("f1", 6_000))
        assertEquals(0, dao.resolve("missing", 6_000))
        assertEquals(5_000L, stored("f1").resolvedAt)
        assertEquals(5_000L, stored("f1").updatedAt)
    }

    @Test
    fun softDeletedFlagIsHiddenButNotRemoved() = runBlocking {
        dao.insert(flag("f1"))

        assertEquals(1, dao.softDelete("f1", 7_000))
        assertEquals(0, dao.softDelete("f1", 8_000))
        assertTrue(dao.observeAllOpen().first().isEmpty())
        assertEquals(0, dao.resolve("f1", 9_000))
        assertEquals(7_000L, stored("f1").deletedAt)
    }

    @Test
    fun aNoteLongerThanTheLimitIsRefused() = runBlocking {
        dao.insert(flag("f1").copy(note = "x".repeat(MAX_FLAG_NOTE_LENGTH)))
        assertFailsWith<IllegalArgumentException> { dao.insert(flag("f2").copy(note = "x".repeat(MAX_FLAG_NOTE_LENGTH + 1))) }
        assertEquals(listOf("f1"), dao.observeAllOpen().first().map { it.id })
    }

    @Test
    fun flagNeedsAnExistingArticle() {
        assertFailsWith<SQLiteException> { runBlocking { dao.insert(flag("f1", articleId = "nope")) } }
    }

    @Test
    fun dateNoticeIsRecordedOnce() = runBlocking {
        val notices = db.dateAlertNoticeDao()
        val notice = DateAlertNotice("a1", "expiry", "2026-12-01", 1_000)

        assertFalse(notices.isNotified("a1", "expiry", "2026-12-01"))
        assertTrue(notices.markNotified(notice))
        assertFalse(notices.markNotified(notice.copy(notifiedAt = 2_000)))
        assertTrue(notices.isNotified("a1", "expiry", "2026-12-01"))
        // Another date of the same attribute is a new notice
        assertTrue(notices.markNotified(notice.copy(dateValue = "2027-01-01")))
    }

    private suspend fun sql(statement: String) = db.useWriterConnection { it.execSQL(statement) }

    @Test
    fun forArticleJoinsTheAuthorNameAndKeepsResolvedFlags() = runBlocking {
        sql("INSERT INTO server_user (user_id, display_name) VALUES ('u1', 'Ann')")
        dao.insert(flag("f1", createdAt = 1_000).copy(createdBy = "u1"))
        dao.insert(flag("f2", createdAt = 2_000).copy(createdBy = "unknown-user"))
        dao.insert(flag("f3", createdAt = 3_000))
        dao.insert(flag("f4", createdAt = 4_000))
        dao.insert(flag("other", articleId = "a2"))
        dao.resolve("f4", 5_000)
        dao.softDelete("f3", 6_000)

        val rows = dao.observeForArticle("a1").first()

        assertEquals(listOf("f1", "f2", "f4"), rows.map { it.flag.id })
        assertEquals("Ann", rows[0].authorName)
        // Author not in server_user (or standalone): no name
        assertNull(rows[1].authorName)
        assertEquals(5_000L, rows[2].flag.resolvedAt)
    }

    @Test
    fun forArticleHasNoAuthorInStandalone() = runBlocking {
        dao.insert(flag("f1"))
        assertNull(dao.observeForArticle("a1").first().single().authorName)
    }

    private suspend fun reviewFlag(id: String, articleId: String, status: String = "finished", fixedAt: Long? = null, deletedAt: Long? = null) {
        sql("INSERT OR IGNORE INTO location (id, name, created_at, updated_at) VALUES ('shelf', 'Shelf', 0, 0)")
        sql(
            "INSERT INTO stock_review (id, location_id, status, started_at, finished_at, created_at, updated_at) " +
                "VALUES ('r-$id', 'shelf', '$status', 1, ${if (status == "finished") 2 else "NULL"}, 1, 1)",
        )
        sql(
            "INSERT INTO stock_review_line (id, review_id, article_id, item_id, location_id, expected_quantity, result, " +
                "info_flagged, info_fixed_at, created_at, updated_at, deleted_at) VALUES ('$id', 'r-$id', '$articleId', NULL, " +
                "'shelf', 0, 'unchecked', 1, ${fixedAt ?: "NULL"}, 1, 1, ${deletedAt ?: "NULL"})",
        )
    }

    @Test
    fun flaggedArticleIdsCoverManualAndReviewFlagsOnce() = runBlocking {
        db.articleDao().insert(Article(id = "a3", sku = "S3", name = "Three", createdAt = 1, updatedAt = 1))
        db.articleDao().insert(Article(id = "a4", sku = "S4", name = "Four", createdAt = 1, updatedAt = 1))
        dao.insert(flag("f1", articleId = "a1"))
        dao.insert(flag("f1b", articleId = "a1"))
        reviewFlag("line-a1", "a1")
        reviewFlag("line-a2", "a2")

        assertEquals(setOf("a1", "a2"), dao.observeFlaggedArticleIds().first().toSet())
        assertEquals(2, dao.observeFlaggedArticleIds().first().size)
    }

    @Test
    fun flaggedArticleIdsLeaveOutResolvedDeletedAndUnfinishedFlags() = runBlocking {
        db.articleDao().insert(Article(id = "a3", sku = "S3", name = "Three", createdAt = 1, updatedAt = 1))
        dao.insert(flag("resolved", articleId = "a1"))
        dao.insert(flag("deleted", articleId = "a2"))
        dao.resolve("resolved", 2_000)
        dao.softDelete("deleted", 2_000)
        reviewFlag("fixed-line", "a3", fixedAt = 5)
        reviewFlag("open-review", "a3", status = "open")

        assertTrue(dao.observeFlaggedArticleIds().first().isEmpty())
    }

    @Test
    fun resolvingTheLastFlagClearsTheBadge() = runBlocking {
        dao.insert(flag("f1"))
        assertEquals(listOf("a1"), dao.observeFlaggedArticleIds().first())

        assertEquals(1, dao.resolve("f1", 3_000))

        assertTrue(dao.observeFlaggedArticleIds().first().isEmpty())
    }

    private suspend fun stored(id: String): ArticleFlag = db.backupDao().snapshot().articleFlags.single { it.id == id }
}

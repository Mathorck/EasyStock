package ch.mathorck.easystock.data.db

import androidx.room.Room
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

    private suspend fun stored(id: String): ArticleFlag = db.backupDao().snapshot().articleFlags.single { it.id == id }
}

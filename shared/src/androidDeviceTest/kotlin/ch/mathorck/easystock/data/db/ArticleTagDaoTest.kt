package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Linking, moving and removing NFC tags ([ArticleDao.linkTag], [ArticleDao.unlinkTag], [ArticleFormDao.createArticle]). */
class ArticleTagDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleDao
    private lateinit var form: ArticleFormDao

    private val tag = "04A23B127C8000"

    private fun article(id: String, barcode: String? = null, deletedAt: Long? = null, archived: Boolean = false) = Article(
        id = id,
        name = "Article $id",
        barcode = barcode,
        archived = archived,
        createdAt = 1_000,
        updatedAt = 1_000,
        deletedAt = deletedAt,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.articleDao()
        form = db.articleFormDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stored(id: String) = requireNotNull(dao.getById(id))

    @Test
    fun freeTagIsLinked() = runBlocking {
        dao.insert(article("a1"))

        assertEquals(TagLinkResult.Linked, dao.linkTag("a1", tag, confirmedOwnerId = null, now = 2_000))

        assertEquals(tag, stored("a1").barcode)
        assertEquals(2_000, stored("a1").updatedAt)
        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun relinkingTheSameTagChangesNothing() = runBlocking {
        dao.insert(article("a1", barcode = tag))

        assertEquals(TagLinkResult.Linked, dao.linkTag("a1", tag, confirmedOwnerId = null, now = 2_000))

        assertEquals(1_000, stored("a1").updatedAt)
    }

    @Test
    fun replacingTheTagSetsTheNewOne() = runBlocking {
        dao.insert(article("a1", barcode = tag))

        assertEquals(TagLinkResult.Linked, dao.linkTag("a1", "AABBCCDD", confirmedOwnerId = null, now = 2_000))

        assertEquals("AABBCCDD", stored("a1").barcode)
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun tagOnAnotherArticleNeedsAConfirmation() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        dao.insert(article("a1"))

        val result = dao.linkTag("a1", tag, confirmedOwnerId = null, now = 2_000)

        assertEquals("owner", assertIs<TagLinkResult.LinkedElsewhere>(result).owner.id)
        assertEquals(tag, stored("owner").barcode)
        assertEquals(1_000, stored("owner").updatedAt)
        assertNull(stored("a1").barcode)
    }

    @Test
    fun confirmedMoveClearsTheOwnerAndSetsTheTarget() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        dao.insert(article("a1"))

        assertEquals(TagLinkResult.Linked, dao.linkTag("a1", tag, confirmedOwnerId = "owner", now = 2_000))

        assertNull(stored("owner").barcode)
        assertEquals(2_000, stored("owner").updatedAt)
        assertEquals(tag, stored("a1").barcode)
        assertEquals(2_000, stored("a1").updatedAt)
    }

    @Test
    fun confirmationForAnotherOwnerDoesNotMoveTheTag() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        dao.insert(article("a1"))

        val result = dao.linkTag("a1", tag, confirmedOwnerId = "someone-else", now = 2_000)

        assertIs<TagLinkResult.LinkedElsewhere>(result)
        assertEquals(tag, stored("owner").barcode)
    }

    @Test
    fun tagHeldByADeletedRowIsFreedWithoutAsking() = runBlocking {
        // A backup merge can leave a deleted row with a tag
        dao.insert(article("gone", barcode = tag, deletedAt = 1_500))
        dao.insert(article("a1"))

        assertEquals(TagLinkResult.Linked, dao.linkTag("a1", tag, confirmedOwnerId = null, now = 2_000))

        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun deletedOrUnknownArticleIsNotLinked() = runBlocking {
        dao.insert(article("gone", deletedAt = 1_500))

        assertEquals(TagLinkResult.ArticleGone, dao.linkTag("gone", tag, confirmedOwnerId = null, now = 2_000))
        assertEquals(TagLinkResult.ArticleGone, dao.linkTag("missing", tag, confirmedOwnerId = null, now = 2_000))
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun unlinkClearsTheTag() = runBlocking {
        dao.insert(article("a1", barcode = tag))

        assertTrue(dao.unlinkTag("a1", now = 2_000))

        assertNull(stored("a1").barcode)
        assertEquals(2_000, stored("a1").updatedAt)
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun unlinkWithoutTagChangesNothing() = runBlocking {
        dao.insert(article("a1"))

        assertFalse(dao.unlinkTag("a1", now = 2_000))
        assertFalse(dao.unlinkTag("missing", now = 2_000))

        assertEquals(1_000, stored("a1").updatedAt)
    }

    @Test
    fun untaggedListsActiveArticlesWithoutTagByName() = runBlocking {
        dao.insert(article("b").copy(name = "bolt"))
        dao.insert(article("a").copy(name = "Anchor"))
        dao.insert(article("tagged", barcode = tag))
        dao.insert(article("archived", archived = true))
        dao.insert(article("deleted", deletedAt = 1_500))

        assertEquals(listOf("a", "b"), dao.observeUntagged().first().map { it.id })
    }

    @Test
    fun createdArticleGetsItsTag() = runBlocking {
        form.createArticle(article("a1", barcode = tag), newCategory = null)

        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun createWithATagOfAnActiveArticleFails() = runBlocking<Unit> {
        dao.insert(article("owner", barcode = tag))

        assertFailsWith<TagAlreadyLinkedException> { form.createArticle(article("a1", barcode = tag), newCategory = null) }

        assertNull(dao.getById("a1"))
        assertEquals("owner", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun createWithATagOfADeletedRowFreesIt() = runBlocking {
        dao.insert(article("gone", barcode = tag, deletedAt = 1_500))

        form.createArticle(article("a1", barcode = tag), newCategory = null)

        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }
}

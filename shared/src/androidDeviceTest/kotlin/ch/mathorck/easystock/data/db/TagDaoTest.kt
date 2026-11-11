package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
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

/**
 * Linking, moving and removing NFC tags of articles and locations ([TagDao], [ArticleFormDao.createArticle],
 * [LocationDao.createLocation]). One tag = one thing, across both tables.
 */
class TagDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleDao
    private lateinit var form: ArticleFormDao
    private lateinit var tags: TagDao
    private lateinit var locations: LocationDao

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
        tags = db.tagDao()
        locations = db.locationDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stored(id: String) = requireNotNull(dao.getById(id))

    @Test
    fun freeTagIsLinked() = runBlocking {
        dao.insert(article("a1"))

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = null, now = 2_000))

        assertEquals(tag, stored("a1").barcode)
        assertEquals(2_000, stored("a1").updatedAt)
        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun relinkingTheSameTagChangesNothing() = runBlocking {
        dao.insert(article("a1", barcode = tag))

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = null, now = 2_000))

        assertEquals(1_000, stored("a1").updatedAt)
    }

    @Test
    fun replacingTheTagSetsTheNewOne() = runBlocking {
        dao.insert(article("a1", barcode = tag))

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), "AABBCCDD", confirmedOwner = null, now = 2_000))

        assertEquals("AABBCCDD", stored("a1").barcode)
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun tagOnAnotherArticleNeedsAConfirmation() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        dao.insert(article("a1"))

        val result = tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = null, now = 2_000)

        assertEquals(TagTarget.OfArticle("owner"), assertIs<TagLinkResult.LinkedElsewhere>(result).owner.target)
        assertEquals(tag, stored("owner").barcode)
        assertEquals(1_000, stored("owner").updatedAt)
        assertNull(stored("a1").barcode)
    }

    @Test
    fun confirmedMoveClearsTheOwnerAndSetsTheTarget() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        dao.insert(article("a1"))

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = TagTarget.OfArticle("owner"), now = 2_000))

        assertNull(stored("owner").barcode)
        assertEquals(2_000, stored("owner").updatedAt)
        assertEquals(tag, stored("a1").barcode)
        assertEquals(2_000, stored("a1").updatedAt)
    }

    @Test
    fun confirmationForAnotherOwnerDoesNotMoveTheTag() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        dao.insert(article("a1"))

        val result = tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = TagTarget.OfArticle("someone-else"), now = 2_000)

        assertIs<TagLinkResult.LinkedElsewhere>(result)
        assertEquals(tag, stored("owner").barcode)
    }

    @Test
    fun tagHeldByADeletedRowIsFreedWithoutAsking() = runBlocking {
        // A backup merge can leave a deleted row with a tag
        dao.insert(article("gone", barcode = tag, deletedAt = 1_500))
        dao.insert(article("a1"))

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = null, now = 2_000))

        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun deletedOrUnknownArticleIsNotLinked() = runBlocking {
        dao.insert(article("gone", deletedAt = 1_500))

        assertEquals(TagLinkResult.TargetGone, tags.linkTag(TagTarget.OfArticle("gone"), tag, confirmedOwner = null, now = 2_000))
        assertEquals(TagLinkResult.TargetGone, tags.linkTag(TagTarget.OfArticle("missing"), tag, confirmedOwner = null, now = 2_000))
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun unlinkClearsTheTag() = runBlocking {
        dao.insert(article("a1", barcode = tag))

        assertTrue(tags.unlinkTag(TagTarget.OfArticle("a1"), now = 2_000))

        assertNull(stored("a1").barcode)
        assertEquals(2_000, stored("a1").updatedAt)
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun unlinkWithoutTagChangesNothing() = runBlocking {
        dao.insert(article("a1"))

        assertFalse(tags.unlinkTag(TagTarget.OfArticle("a1"), now = 2_000))
        assertFalse(tags.unlinkTag(TagTarget.OfArticle("missing"), now = 2_000))

        assertEquals(1_000, stored("a1").updatedAt)
    }

    @Test
    fun untaggedListsActiveArticlesWithoutTagByName() = runBlocking {
        dao.insert(article("b").copy(name = "bolt"))
        dao.insert(article("a").copy(name = "Anchor"))
        dao.insert(article("tagged", barcode = tag))
        dao.insert(article("archived", archived = true))
        dao.insert(article("deleted", deletedAt = 1_500))

        assertEquals(listOf("a", "b"), tags.observeUntaggedArticles().first().map { it.id })
    }

    @Test
    fun taggedListsActiveArticlesWithATagByName() = runBlocking {
        dao.insert(article("b", barcode = "0B").copy(name = "bolt"))
        dao.insert(article("a", barcode = "0A").copy(name = "Anchor"))
        dao.insert(article("archived", barcode = "0C", archived = true).copy(name = "Clamp"))
        dao.insert(article("untagged").copy(name = "Drill"))
        // A deleted row still holding a tag (older data): not listed
        dao.insert(article("deleted", barcode = "0D", deletedAt = 1_500).copy(name = "Eraser"))

        assertEquals(listOf("a", "b", "archived"), tags.observeTaggedArticles().first().map { it.id })
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

    // Locations

    private suspend fun location(id: String) = requireNotNull(db.articleDetailDao().observeAllLocations().first().firstOrNull { it.id == id })

    @Test
    fun locationTagIsLinkedFoundAndRemoved() = runBlocking {
        locations.createLocation("crate", "Crate", null, 1_000)

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfLocation("crate"), tag, confirmedOwner = null, now = 2_000))

        assertEquals(tag, location("crate").barcode)
        assertEquals(2_000, location("crate").updatedAt)
        assertEquals(TagTarget.OfLocation("crate"), tags.findOwner(tag)?.target)

        assertTrue(tags.unlinkTag(TagTarget.OfLocation("crate"), now = 3_000))
        assertNull(location("crate").barcode)
        assertNull(tags.findOwner(tag))
        assertFalse(tags.unlinkTag(TagTarget.OfLocation("crate"), now = 3_000))
    }

    @Test
    fun articleTagNeedsAConfirmationToMoveToALocation() = runBlocking {
        dao.insert(article("owner", barcode = tag))
        locations.createLocation("crate", "Crate", null, 1_000)

        val result = tags.linkTag(TagTarget.OfLocation("crate"), tag, confirmedOwner = null, now = 2_000)
        assertEquals(TagTarget.OfArticle("owner"), assertIs<TagLinkResult.LinkedElsewhere>(result).owner.target)
        assertEquals(tag, stored("owner").barcode)
        assertNull(location("crate").barcode)

        val moved = tags.linkTag(TagTarget.OfLocation("crate"), tag, confirmedOwner = TagTarget.OfArticle("owner"), now = 2_000)
        assertEquals(TagLinkResult.Linked, moved)
        assertNull(stored("owner").barcode)
        assertEquals(2_000, stored("owner").updatedAt)
        assertEquals(tag, location("crate").barcode)
        assertNull(dao.findByBarcode(tag))
    }

    @Test
    fun locationTagNeedsAConfirmationToMoveToAnArticle() = runBlocking {
        locations.createLocation("crate", "Crate", null, 1_000, barcode = tag)
        dao.insert(article("a1"))

        val result = tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = null, now = 2_000)
        assertIs<TagLinkResult.LinkedElsewhere>(result)
        // A location with the same ID as the owner is not a confirmation for the article
        assertIs<TagLinkResult.LinkedElsewhere>(tags.linkTag(TagTarget.OfArticle("a1"), tag, TagTarget.OfArticle("crate"), 2_000))

        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), tag, TagTarget.OfLocation("crate"), 2_000))
        assertNull(location("crate").barcode)
        assertEquals(tag, stored("a1").barcode)
        assertEquals(TagTarget.OfArticle("a1"), tags.findOwner(tag)?.target)
    }

    @Test
    fun tagOfADeletedLocationIsFreedWithoutAsking() = runBlocking {
        locations.createLocation("gone", "Gone", null, 1_000)
        dao.insert(article("a1"))
        // A backup merge can leave a deleted row with a tag
        db.useWriterConnection { it.execSQL("UPDATE location SET barcode = '$tag', deleted_at = 1500 WHERE id = 'gone'") }

        assertNull(tags.findOwner(tag))
        assertEquals(TagLinkResult.Linked, tags.linkTag(TagTarget.OfArticle("a1"), tag, confirmedOwner = null, now = 2_000))

        assertNull(location("gone").barcode)
        assertEquals(tag, stored("a1").barcode)
    }

    @Test
    fun deletedLocationIsNotLinked() = runBlocking {
        locations.createLocation("gone", "Gone", null, 1_000)
        locations.softDeleteLocation("gone", 1_500)

        assertEquals(TagLinkResult.TargetGone, tags.linkTag(TagTarget.OfLocation("gone"), tag, confirmedOwner = null, now = 2_000))
        assertEquals(TagLinkResult.TargetGone, tags.linkTag(TagTarget.OfLocation("missing"), tag, confirmedOwner = null, now = 2_000))
    }

    @Test
    fun softDeletingALocationClearsItsTag() = runBlocking {
        locations.createLocation("room", "Room", null, 1_000)
        locations.createLocation("crate", "Crate", "room", 1_000, barcode = tag)

        locations.softDeleteLocation("room", 2_000)

        assertNull(location("crate").barcode)
        assertEquals(2_000, location("crate").deletedAt)
        assertNull(tags.findOwner(tag))
        // The tag can be reused
        locations.createLocation("box", "Box", null, 3_000, barcode = tag)
        assertEquals(TagTarget.OfLocation("box"), tags.findOwner(tag)?.target)
    }

    @Test
    fun createdLocationGetsItsTagUnlessSomethingActiveHoldsIt() = runBlocking<Unit> {
        locations.createLocation("crate", "Crate", null, 1_000, barcode = tag)
        assertEquals(TagTarget.OfLocation("crate"), tags.findOwner(tag)?.target)

        assertFailsWith<TagAlreadyLinkedException> { locations.createLocation("box", "Box", null, 1_000, barcode = tag) }
        dao.insert(article("owner", barcode = "AABBCCDD"))
        assertFailsWith<TagAlreadyLinkedException> { locations.createLocation("bin", "Bin", null, 1_000, barcode = "AABBCCDD") }

        assertEquals(listOf("crate"), locations.activeLocations().map { it.id })
    }

    @Test
    fun createdLocationFreesTheTagOfADeletedArticle() = runBlocking {
        dao.insert(article("gone", barcode = tag, deletedAt = 1_500))

        locations.createLocation("crate", "Crate", null, 2_000, barcode = tag)

        // getById skips deleted rows: read the row directly
        val goneTag = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT barcode FROM article WHERE id = 'gone'") { if (it.step() && !it.isNull(0)) it.getText(0) else null }
        }
        assertNull(goneTag)
        assertEquals(TagTarget.OfLocation("crate"), tags.findOwner(tag)?.target)
    }

    @Test
    fun createdArticleCannotTakeTheTagOfALocation() = runBlocking<Unit> {
        locations.createLocation("crate", "Crate", null, 1_000, barcode = tag)

        assertFailsWith<TagAlreadyLinkedException> { form.createArticle(article("a1", barcode = tag), newCategory = null) }

        assertNull(dao.getById("a1"))
        assertEquals(tag, location("crate").barcode)
    }

    @Test
    fun locationsListIncludesTaggedOnesForThePaths() = runBlocking {
        locations.createLocation("room", "Room", null, 1_000, barcode = tag)
        locations.createLocation("crate", "crate", "room", 1_000)
        locations.createLocation("gone", "Gone", null, 1_000)
        locations.softDeleteLocation("gone", 1_500)

        assertEquals(listOf("crate", "room"), tags.observeLocations().first().map { it.id })
    }
}

package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArticleDetailDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var articles: ArticleDao
    private lateinit var stock: StockDao
    private lateinit var detail: ArticleDetailDao
    private var movementCount = 0

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        articles = db.articleDao()
        stock = db.stockDao()
        detail = db.articleDetailDao()
        stock.insertLocation(Location(id = "shelf", name = "Shelf", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "van", name = "Van", createdAt = 0, updatedAt = 0))
        stock.insertLocation(Location(id = "old", name = "Old bin", createdAt = 0, updatedAt = 0, deletedAt = 1))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun article(id: String, sku: String? = id, categoryId: String? = null) =
        articles.insert(
            Article(
                id = id, sku = sku, name = id, categoryId = categoryId, barcode = "tag-$id",
                thresholdMilli = 5_000, createdAt = 10, updatedAt = 10,
            ),
        )

    private suspend fun move(
        articleId: String,
        quantityMilli: Long,
        location: String = "shelf",
        occurredAt: Long = 0,
        createdAt: Long = occurredAt,
        createdBy: String? = null,
    ): String {
        val id = "m${movementCount++}"
        stock.insertMovement(
            StockMovement(
                id = id,
                articleId = articleId,
                locationId = location,
                quantityMilli = quantityMilli,
                reason = MovementReason.INVENTORY,
                occurredAt = occurredAt,
                createdAt = createdAt,
                createdBy = createdBy,
            ),
        )
        return id
    }

    private suspend fun categoryCount() = db.articleFormDao().countCategories()

    private fun changes(
        name: String = "Renamed",
        sku: String? = "NEW-1",
        categoryId: String? = null,
        thresholdMilli: Long? = 0,
        imagePath: String? = null,
        nameAuto: Boolean = false,
        attributes: JsonObject? = null,
    ) = ArticleChanges(
        name = name, nameAuto = nameAuto, sku = sku, description = "Desc", categoryId = categoryId, unit = "kg",
        thresholdMilli = thresholdMilli, imagePath = imagePath, attributes = attributes,
    )

    // ---------- Reads ----------

    @Test
    fun articleWithCategoryName() = runBlocking<Unit> {
        db.categoryDao().insert(Category(id = "c1", name = "Hardware", createdAt = 0, updatedAt = 0))
        article("a1", categoryId = "c1")

        val result = assertNotNull(detail.observeArticle("a1").first())
        assertEquals("a1", result.article.id)
        assertEquals("Hardware", result.categoryName)
    }

    @Test
    fun deletedArticleIsNull() = runBlocking<Unit> {
        article("a1")
        articles.softDelete("a1", now = 20)

        assertNull(detail.observeArticle("a1").first())
        assertNull(detail.observeArticle("missing").first())
    }

    @Test
    fun stockPerLocationSkipsZeroAndOtherArticles() = runBlocking<Unit> {
        article("a1")
        article("a2")
        move("a1", 10_000, "shelf")
        move("a1", -2_500, "shelf")
        move("a1", 3_000, "van")
        move("a1", -3_000, "van")
        move("a1", 1_000, "old")
        move("a2", 7_000, "van")

        val result = detail.observeStockByLocation("a1").first().associate { it.locationId to it.stockMilli }
        assertEquals(mapOf("shelf" to 7_500L, "old" to 1_000L), result)
    }

    @Test
    fun movementsNewestFirstWithLocationName() = runBlocking<Unit> {
        article("a1")
        val oldest = move("a1", 1_000, "shelf", occurredAt = 100)
        val newest = move("a1", 2_000, "old", occurredAt = 300)
        // Same occurred_at: the most recently recorded comes first
        val middleLate = move("a1", -500, "van", occurredAt = 200, createdAt = 250)
        val middleEarly = move("a1", -100, "van", occurredAt = 200, createdAt = 210)
        article("a2")
        move("a2", 9_000, "shelf", occurredAt = 400)

        val result = detail.observeMovements("a1").first()
        assertEquals(listOf(newest, middleLate, middleEarly, oldest), result.map { it.movement.id })
        assertEquals(listOf("Old bin", "Van", "Van", "Shelf"), result.map { it.locationName })
    }

    @Test
    fun movementAuthorComesFromServerUser() = runBlocking<Unit> {
        sql("INSERT INTO server_user (user_id, display_name) VALUES ('u1', 'Anna')")
        article("a1")
        val known = move("a1", 1_000, occurredAt = 300, createdBy = "u1")
        val unknownUser = move("a1", 1_000, occurredAt = 200, createdBy = "u2")
        val standalone = move("a1", 1_000, occurredAt = 100)

        val result = detail.observeMovements("a1").first()
        assertEquals(listOf(known, unknownUser, standalone), result.map { it.movement.id })
        assertEquals(listOf("Anna", null, null), result.map { it.authorName })
    }

    @Test
    fun allLocationsIncludeDeleted() = runBlocking<Unit> {
        assertEquals(setOf("shelf", "van", "old"), detail.observeAllLocations().first().map { it.id }.toSet())
    }

    @Test
    fun skusIncludeDeletedArticlesAndSkipNull() = runBlocking<Unit> {
        article("a1", sku = "A-1")
        article("a2", sku = null)
        article("a3", sku = "A-3")
        articles.softDelete("a3", now = 20)

        assertEquals(setOf(SkuOwner("a1", "A-1"), SkuOwner("a3", "A-3")), detail.observeSkus().first().toSet())
    }

    // ---------- Update ----------

    @Test
    fun updateChangesOnlyEditedFields() = runBlocking<Unit> {
        article("a1")
        move("a1", 4_000)

        detail.updateArticle("a1", changes(), newCategory = null, now = 99)

        val updated = assertNotNull(articles.getById("a1"))
        assertEquals("Renamed", updated.name)
        assertEquals("NEW-1", updated.sku)
        assertEquals("Desc", updated.description)
        assertEquals("kg", updated.unit)
        assertEquals(0L, updated.thresholdMilli)
        assertEquals(99L, updated.updatedAt)
        assertEquals(10L, updated.createdAt)
        assertEquals("tag-a1", updated.barcode)
        assertEquals(4_000L, articles.observeStock().first().single().stockMilli)
    }

    @Test
    fun updateSavesTheAttributesAndTheNameMode() = runBlocking<Unit> {
        article("a1")
        val values = JsonObject(mapOf("note" to JsonPrimitive("red")))

        detail.updateArticle("a1", changes(nameAuto = true, attributes = values), newCategory = null, now = 99)
        val updated = assertNotNull(articles.getById("a1"))
        assertTrue(updated.nameAuto)
        assertEquals(values, updated.attributes)

        detail.updateArticle("a1", changes(), newCategory = null, now = 100)
        val cleared = assertNotNull(articles.getById("a1"))
        assertFalse(cleared.nameAuto)
        assertNull(cleared.attributes)
    }

    @Test
    fun sameSkuWithOtherCaseIsAllowedForTheSameArticle() = runBlocking<Unit> {
        article("a1", sku = "abc")

        detail.updateArticle("a1", changes(sku = "ABC"), newCategory = null, now = 99)

        assertEquals("ABC", articles.getById("a1")?.sku)
    }

    @Test
    fun duplicateSkuFailsWithoutPartialCategory() = runBlocking<Unit> {
        article("a1")
        article("a2", sku = "TAKEN")
        articles.softDelete("a2", now = 20)
        val newCategory = Category(id = "c-new", name = "New", createdAt = 99, updatedAt = 99)

        assertFailsWith<DuplicateSkuException> {
            detail.updateArticle("a1", changes(sku = "taken", categoryId = "c-new"), newCategory, now = 99)
        }

        assertEquals(0, categoryCount())
        assertEquals("a1", articles.getById("a1")?.name)
    }

    @Test
    fun clearingSkuToNullWorks() = runBlocking<Unit> {
        article("a1", sku = "A-1")

        detail.updateArticle("a1", changes(sku = null), newCategory = null, now = 99)

        assertNull(articles.getById("a1")?.sku)
    }

    @Test
    fun twoArticlesWithNullSkuAreAllowed() = runBlocking<Unit> {
        article("a1", sku = null)
        article("a2", sku = "A-2")

        detail.updateArticle("a2", changes(sku = null), newCategory = null, now = 99)

        assertNull(articles.getById("a1")?.sku)
        assertNull(articles.getById("a2")?.sku)
    }

    @Test
    fun newCategoryIsCreatedAndLinked() = runBlocking<Unit> {
        article("a1")
        val newCategory = Category(id = "c-new", name = "Tools", createdAt = 99, updatedAt = 99)

        detail.updateArticle("a1", changes(categoryId = "c-new"), newCategory, now = 99)

        assertEquals("c-new", articles.getById("a1")?.categoryId)
        assertEquals(listOf("Tools"), db.categoryDao().observeAll().first().map { it.name })
    }

    @Test
    fun activeCategoryWithSameNameIsReused() = runBlocking<Unit> {
        db.categoryDao().insert(Category(id = "c1", name = "Tools", createdAt = 0, updatedAt = 0))
        article("a1")
        val newCategory = Category(id = "c-new", name = "tools", createdAt = 99, updatedAt = 99)

        detail.updateArticle("a1", changes(categoryId = "c-new"), newCategory, now = 99)

        assertEquals("c1", articles.getById("a1")?.categoryId)
        assertEquals(1, categoryCount())
    }

    @Test
    fun deletedCategoryNameFails() = runBlocking<Unit> {
        db.categoryDao().insert(Category(id = "c1", name = "Tools", createdAt = 0, updatedAt = 0, deletedAt = 5))
        article("a1")
        val newCategory = Category(id = "c-new", name = "TOOLS", createdAt = 99, updatedAt = 99)

        assertFailsWith<DeletedCategoryNameException> {
            detail.updateArticle("a1", changes(categoryId = "c-new"), newCategory, now = 99)
        }

        assertEquals(1, categoryCount())
        assertEquals("a1", articles.getById("a1")?.name)
    }

    @Test
    fun deletedArticleCannotBeUpdated() = runBlocking<Unit> {
        article("a1")
        articles.softDelete("a1", now = 20)

        assertFailsWith<ArticleNotFoundException> {
            detail.updateArticle("a1", changes(), newCategory = null, now = 99)
        }
    }

    // ---------- Photo ----------

    private val photo1 = "images/00000000-0000-4000-8000-000000000001.jpg"
    private val photo2 = "images/00000000-0000-4000-8000-000000000002.jpg"

    @Test
    fun updateStoresThePhotoAndReturnsThePreviousOne() = runBlocking<Unit> {
        article("a1")

        assertNull(detail.updateArticle("a1", changes(imagePath = photo1), newCategory = null, now = 20))
        assertEquals(photo1, articles.getById("a1")?.imagePath)

        assertEquals(photo1, detail.updateArticle("a1", changes(imagePath = null), newCategory = null, now = 30))
        assertNull(articles.getById("a1")?.imagePath)
    }

    @Test
    fun setImagePathReplacesAndReturnsThePreviousOne() = runBlocking<Unit> {
        article("a1")

        assertNull(detail.setImagePath("a1", photo1, now = 20))
        assertEquals(photo1, articles.getById("a1")?.imagePath)
        assertEquals(20, articles.getById("a1")?.updatedAt)

        assertEquals(photo1, detail.setImagePath("a1", photo2, now = 30))
        val stored = assertNotNull(articles.getById("a1"))
        assertEquals(photo2, stored.imagePath)
        assertEquals(30, stored.updatedAt)
        // The other fields are kept
        assertEquals("a1", stored.name)
        assertEquals("tag-a1", stored.barcode)
    }

    @Test
    fun setImagePathToNullRemovesThePhoto() = runBlocking<Unit> {
        article("a1")
        detail.setImagePath("a1", photo1, now = 20)

        assertEquals(photo1, detail.setImagePath("a1", null, now = 30))
        assertNull(articles.getById("a1")?.imagePath)
    }

    @Test
    fun samePhotoPathKeepsUpdatedAt() = runBlocking<Unit> {
        article("a1")
        detail.setImagePath("a1", photo1, now = 20)

        assertEquals(photo1, detail.setImagePath("a1", photo1, now = 30))
        assertEquals(20, articles.getById("a1")?.updatedAt)
    }

    @Test
    fun deletedArticlePhotoCannotBeSet() = runBlocking<Unit> {
        article("a1")
        articles.softDelete("a1", now = 20)

        assertFailsWith<ArticleNotFoundException> { detail.setImagePath("a1", photo1, now = 30) }
    }

    // ---------- Info to fix flags ----------

    private suspend fun sql(statement: String) = db.useWriterConnection { it.execSQL(statement) }

    private suspend fun review(id: String, status: String, finishedAt: Long?, deletedAt: Long? = null) = sql(
        "INSERT INTO stock_review (id, location_id, status, started_at, finished_at, created_at, updated_at, deleted_at) " +
            "VALUES ('$id', 'shelf', '$status', 1, ${finishedAt ?: "NULL"}, 1, 1, ${deletedAt ?: "NULL"})",
    )

    private suspend fun line(
        id: String,
        reviewId: String,
        articleId: String = "a1",
        flagged: Boolean = true,
        note: String? = "Change the box",
        location: String = "shelf",
        itemId: String? = null,
        deletedAt: Long? = null,
    ) = sql(
        "INSERT INTO stock_review_line (id, review_id, article_id, item_id, location_id, expected_quantity, result, " +
            "info_flagged, note, created_at, updated_at, deleted_at) VALUES ('$id', '$reviewId', '$articleId', " +
            "${itemId?.let { "'$it'" } ?: "NULL"}, '$location', 0, 'unchecked', ${if (flagged) 1 else 0}, " +
            "${note?.let { "'$it'" } ?: "NULL"}, 1, 1, ${deletedAt ?: "NULL"})",
    )

    private suspend fun reviewLine(reviewId: String, id: String) = db.reviewDao().linesOf(reviewId).single { it.id == id }

    @Test
    fun infoFlagsOnlyFromFinishedReviews() = runBlocking<Unit> {
        article("a1")
        article("a2")
        review("finished", "finished", finishedAt = 500)
        review("older", "finished", finishedAt = 300)
        review("open", "open", finishedAt = null)
        review("cancelled", "cancelled", finishedAt = 400)
        review("deleted", "finished", finishedAt = 450, deletedAt = 460)
        line("flag", "finished", note = "Change the box", location = "old")
        line("flag-old", "older", note = null)
        line("not-flagged", "finished", flagged = false, note = "Leftover note")
        line("deleted-line", "finished", deletedAt = 600)
        line("other-article", "finished", articleId = "a2")
        line("in-open", "open")
        line("in-cancelled", "cancelled")
        line("in-deleted", "deleted")

        val flags = detail.observeInfoFlags("a1").first()

        assertEquals(listOf("flag", "flag-old"), flags.map { it.lineId })
        assertEquals("Change the box", flags[0].note)
        assertEquals(500L, flags[0].flaggedAt)
        // Soft-deleted location: still joined, by name
        assertEquals("Old bin", flags[0].locationName)
        assertNull(flags[0].fixedAt)
        assertNull(flags[1].note)
    }

    @Test
    fun infoFlagOfAUnitHasItsSerialNumber() = runBlocking<Unit> {
        article("a1")
        sql("INSERT INTO item (id, article_id, serial_number, status, location_id, created_at, updated_at) VALUES ('i1', 'a1', 'SN-1', 'available', 'shelf', 1, 1)")
        review("r", "finished", finishedAt = 500)
        line("unit", "r", itemId = "i1")

        assertEquals("SN-1", detail.observeInfoFlags("a1").first().single().serialNumber)
    }

    @Test
    fun markInfoFixedClosesTheFlagOnce() = runBlocking<Unit> {
        article("a1")
        review("r", "finished", finishedAt = 500)
        line("flag", "r")

        assertTrue(detail.markInfoFixed("flag", now = 700))
        assertFalse(detail.markInfoFixed("flag", now = 800))

        val flag = detail.observeInfoFlags("a1").first().single()
        assertEquals(700L, flag.fixedAt)
        val stored = reviewLine("r", "flag")
        assertEquals(700L, stored.infoFixedAt)
        // updated_at follows, so a backup merge keeps the fix
        assertEquals(700L, stored.updatedAt)
    }

    @Test
    fun markInfoFixedIgnoresOtherLines() = runBlocking<Unit> {
        article("a1")
        review("open", "open", finishedAt = null)
        review("cancelled", "cancelled", finishedAt = 400)
        review("r", "finished", finishedAt = 500)
        line("in-open", "open")
        line("in-cancelled", "cancelled")
        line("not-flagged", "r", flagged = false)
        line("deleted-line", "r", deletedAt = 600)

        listOf("in-open", "in-cancelled", "not-flagged", "deleted-line", "missing").forEach {
            assertFalse(detail.markInfoFixed(it, now = 700), it)
        }
        assertNull(reviewLine("open", "in-open").infoFixedAt)
        assertEquals(1L, reviewLine("open", "in-open").updatedAt)
    }

    /** End to end: a flag set during a review shows on the article once the review is finished. */
    @Test
    fun flagFromAFinishedReviewShowsOnTheArticle() = runBlocking<Unit> {
        article("a1")
        move("a1", 2_000, "shelf")
        var ids = 0
        val reviews = db.reviewDao()
        val reviewId = reviews.startReview("shelf", now = 100, newId = { "rid${ids++}" })
        val lineId = reviews.linesOf(reviewId).single { it.articleId == "a1" }.id
        reviews.setFlag(lineId, flagged = true, note = "  Change the box  ", now = 110)

        assertTrue(detail.observeInfoFlags("a1").first().isEmpty())

        reviews.finishReview(reviewId, emptyMap(), comment = "Review", now = 200, newId = { "rid${ids++}" })

        val flag = detail.observeInfoFlags("a1").first().single()
        assertEquals(lineId, flag.lineId)
        assertEquals("Change the box", flag.note)
        assertEquals(200L, flag.flaggedAt)
    }
}

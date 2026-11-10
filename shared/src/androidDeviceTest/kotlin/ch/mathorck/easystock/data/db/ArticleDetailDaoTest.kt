package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    ) = ArticleChanges(name = name, sku = sku, description = "Desc", categoryId = categoryId, unit = "kg", thresholdMilli = thresholdMilli)

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
}

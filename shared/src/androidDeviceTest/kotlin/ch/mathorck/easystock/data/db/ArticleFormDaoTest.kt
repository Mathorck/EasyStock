package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArticleFormDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var articles: ArticleDao
    private lateinit var form: ArticleFormDao

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        articles = db.articleDao()
        form = db.articleFormDao()
        db.categoryDao().insert(Category(id = "hw", name = "Hardware", createdAt = 0, updatedAt = 0))
        db.categoryDao().insert(Category(id = "old", name = "Old", createdAt = 0, updatedAt = 0, deletedAt = 1))
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String = "a1", sku: String? = null, categoryId: String? = null, tracking: Tracking = Tracking.QUANTITY) =
        Article(id = id, sku = sku, name = "Article $id", categoryId = categoryId, tracking = tracking, createdAt = 5, updatedAt = 5)

    private fun category(name: String) = Category(id = "new-cat", name = name, createdAt = 5, updatedAt = 5)

    private suspend fun stored(id: String) = requireNotNull(articles.getById(id))

    @Test
    fun createsArticleWithoutCategory() = runBlocking {
        val result = form.createArticle(article(), newCategory = null)

        assertEquals(article(), result)
        assertEquals(article(), stored("a1"))
        assertEquals(2, form.countCategories())
    }

    @Test
    fun createsArticleWithExistingCategory() = runBlocking {
        form.createArticle(article(categoryId = "hw"), newCategory = null)

        assertEquals("hw", stored("a1").categoryId)
    }

    @Test
    fun createsNewCategoryAndLinksIt() = runBlocking {
        val result = form.createArticle(article(), category("Paint"))

        assertEquals("new-cat", result.categoryId)
        assertEquals("new-cat", stored("a1").categoryId)
        assertEquals(listOf("Hardware", "Paint"), db.categoryDao().observeAll().first().map { it.name })
    }

    @Test
    fun newCategoryReusesActiveNameIgnoringCase() = runBlocking {
        val result = form.createArticle(article(), category("HARDWARE"))

        assertEquals("hw", result.categoryId)
        assertEquals(2, form.countCategories())
    }

    @Test
    fun deletedCategoryNameIsRejectedAndNothingIsSaved() = runBlocking<Unit> {
        assertFailsWith<DeletedCategoryNameException> {
            form.createArticle(article(), category("old"))
        }
        assertEquals(0, form.countArticles())
        assertEquals(2, form.countCategories())
    }

    @Test
    fun duplicateSkuIsRejectedIgnoringCaseIncludingDeletedArticles() = runBlocking<Unit> {
        articles.insert(article(id = "gone", sku = "BLT-8").softDeleted(now = 6))

        assertFailsWith<DuplicateSkuException> {
            form.createArticle(article(sku = "blt-8"), category("Paint"))
        }
        assertEquals(1, form.countArticles())
        // The new category is rolled back too
        assertEquals(2, form.countCategories())
    }

    @Test
    fun serialTrackingIsStored() = runBlocking {
        form.createArticle(article(tracking = Tracking.SERIAL), newCategory = null)

        assertEquals(Tracking.SERIAL, stored("a1").tracking)
    }

    @Test
    fun skusIncludeDeletedArticles() = runBlocking {
        articles.insert(article(id = "gone", sku = "OLD-1").softDeleted(now = 6))
        form.createArticle(article(sku = "NEW-1"), newCategory = null)

        assertEquals(setOf(SkuOwner("gone", "OLD-1"), SkuOwner("a1", "NEW-1")), form.observeSkus().first().toSet())
    }
}

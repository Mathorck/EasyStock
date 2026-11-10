package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.SQLiteException
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleDao

    private val tag = "04:A2:3B:12:7C:80:00"

    private fun article(id: String, sku: String, barcode: String? = tag) = Article(
        id = id,
        sku = sku,
        name = "Article $sku",
        barcode = barcode,
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.articleDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertedArticleIsFoundByItsTag() = runBlocking {
        dao.insert(article("a1", "SKU-1"))

        assertEquals("a1", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun twoActiveArticlesCannotShareATag() = runBlocking {
        dao.insert(article("a1", "SKU-1"))

        assertFailsWith<SQLiteException> { dao.insert(article("a2", "SKU-2")) }
        Unit
    }

    @Test
    fun softDeleteHidesTheArticleAndFreesItsTag() = runBlocking {
        dao.insert(article("a1", "SKU-1"))

        assertTrue(dao.softDelete("a1", now = 2_000))

        assertNull(dao.getById("a1"))
        assertNull(dao.findByBarcode(tag))
        assertTrue(dao.observeAll().first().isEmpty())

        dao.insert(article("a2", "SKU-2"))
        assertEquals("a2", dao.findByBarcode(tag)?.id)
    }

    @Test
    fun softDeleteOfUnknownArticleReturnsFalse() = runBlocking {
        assertFalse(dao.softDelete("missing", now = 2_000))
    }

    @Test
    fun observeAllIsSortedByName() = runBlocking {
        dao.insert(article("a1", "B", barcode = null).copy(name = "Bolt"))
        dao.insert(article("a2", "A", barcode = null).copy(name = "Anchor"))

        assertEquals(listOf("Anchor", "Bolt"), dao.observeAll().first().map { it.name })
    }
}

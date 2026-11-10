package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.composables.attributes.NameTemplateError
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Category name templates: saving them with the attributes, and renaming the articles that follow them. */
@OptIn(ExperimentalSerializationApi::class)
class NameTemplateDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var attributes: AttributeDao
    private lateinit var articles: ArticleDao
    private var idCount = 0
    private val newId: () -> String = { "id${idCount++}" }

    private val ropeAttributes = listOf(
        CategoryAttributeInput("type", required = false),
        CategoryAttributeInput("diameter", required = false),
        CategoryAttributeInput("length", required = false),
    )

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        attributes = db.attributeDao()
        articles = db.articleDao()
        db.categoryDao().insert(Category(id = "ropes", name = "Ropes", createdAt = 0, updatedAt = 0))
        db.categoryDao().insert(Category(id = "other", name = "Other", createdAt = 0, updatedAt = 0))
        attributes.createDefinition("type", "Rope type", AttributeDataType.TEXT, null, null, 0)
        attributes.createDefinition("diameter", "Diameter", AttributeDataType.NUMBER, "mm", null, 0)
        attributes.createDefinition("length", "Length", AttributeDataType.NUMBER, "m", null, 0)
        attributes.setCategoryAttributes("ropes", ropeAttributes, 0, newId)
    }

    @After
    fun tearDown() = db.close()

    private fun values(type: String?, diameter: String?, length: String?) = JsonObject(
        buildMap {
            type?.let { put("rope_type", JsonPrimitive(it)) }
            diameter?.let { put("diameter", JsonUnquotedLiteral(it)) }
            length?.let { put("length", JsonUnquotedLiteral(it)) }
        },
    )

    private suspend fun article(
        id: String,
        name: String = id,
        nameAuto: Boolean = true,
        attributes: JsonObject? = values("static", "10.5", "50"),
        categoryId: String = "ropes",
        deletedAt: Long? = null,
    ) = articles.insert(
        Article(
            id = id, name = name, nameAuto = nameAuto, categoryId = categoryId, attributes = attributes,
            createdAt = 1, updatedAt = 1, deletedAt = deletedAt,
        ),
    )

    private suspend fun stored(id: String) = assertNotNull(articles.getById(id))

    private suspend fun save(template: String?, applyToAll: Boolean = false, now: Long = 50) =
        attributes.saveCategorySettings("ropes", ropeAttributes, template, applyToAll, now, newId)

    private suspend fun ropes() = db.categoryDao().observeAll().first().single { it.id == "ropes" }

    @Test
    fun savingATemplateRenamesOnlyTheArticlesThatFollowIt() = runBlocking<Unit> {
        article("auto")
        article("typed", name = "My rope", nameAuto = false)
        article("deleted", deletedAt = 5)
        article("elsewhere", categoryId = "other")

        val renamed = save(" {rope_type} rope {diameter} {length} ")

        assertEquals(1, renamed)
        assertEquals("{rope_type} rope {diameter} {length}", ropes().nameTemplate)
        assertEquals(50, ropes().updatedAt)
        with(stored("auto")) {
            assertEquals("Static rope 10.5 mm 50 m", name)
            assertEquals(50, updatedAt)
        }
        with(stored("typed")) {
            assertEquals("My rope", name)
            assertEquals(1, updatedAt)
        }
        // The soft-deleted article is not counted above (getById hides it)
        assertEquals("elsewhere", stored("elsewhere").name)
    }

    @Test
    fun unchangedNamesAreNotWritten() = runBlocking<Unit> {
        article("auto", name = "Static rope 10.5 mm 50 m")

        assertEquals(0, save("{rope_type} rope {diameter} {length}"))
        assertEquals(1, stored("auto").updatedAt)
    }

    @Test
    fun missingValuesAreLeftOutAndAnEmptyNameKeepsTheOldOne() = runBlocking<Unit> {
        article("partial", attributes = values("static", null, "50"))
        article("empty", name = "Old name", attributes = null)

        assertEquals(1, save("{rope_type} - {diameter} - {length}"))
        assertEquals("Static - 50 m", stored("partial").name)
        assertEquals("Old name", stored("empty").name)
        assertEquals(1, stored("empty").updatedAt)
    }

    @Test
    fun applyToAllMakesTypedNamesFollow() = runBlocking<Unit> {
        article("typed", name = "My rope", nameAuto = false)
        assertEquals(1, attributes.observeManualNameCount("ropes").first())

        assertEquals(1, save("{rope_type} rope", applyToAll = true))
        with(stored("typed")) {
            assertTrue(nameAuto)
            assertEquals("Static rope", name)
            assertEquals(50, updatedAt)
        }
        assertEquals(0, attributes.observeManualNameCount("ropes").first())
    }

    @Test
    fun applyToAllWithoutTemplateChangesNothing() = runBlocking<Unit> {
        article("typed", name = "My rope", nameAuto = false)

        assertEquals(0, save("  ", applyToAll = true))
        assertFalse(stored("typed").nameAuto)
        assertNull(ropes().nameTemplate)
    }

    @Test
    fun removingTheTemplateKeepsTheNames() = runBlocking<Unit> {
        article("auto")
        save("{rope_type} rope")

        assertEquals(0, save("", now = 60))
        assertNull(ropes().nameTemplate)
        assertEquals("Static rope", stored("auto").name)
    }

    @Test
    fun unknownKeyIsRejectedAndNothingIsSaved() = runBlocking<Unit> {
        article("auto")
        val error = assertFailsWith<InvalidNameTemplateException> {
            attributes.saveCategorySettings("ropes", ropeAttributes.take(1), "{rope_type} {length}", false, 50, newId)
        }.error

        assertEquals(NameTemplateError(NameTemplateError.Kind.UNKNOWN_KEY, "length"), error)
        assertNull(ropes().nameTemplate)
        assertEquals(3, attributes.observeCategoryAttributes("ropes").first().size)
        assertEquals("auto", stored("auto").name)
    }

    @Test
    fun deletedCategoryIsRejected() = runBlocking<Unit> {
        db.categoryDao().insert(Category(id = "gone", name = "Gone", createdAt = 0, updatedAt = 0, deletedAt = 1))
        assertFailsWith<CategoryNotFoundException> {
            attributes.saveCategorySettings("gone", emptyList(), "x", false, 50, newId)
        }
    }

    @Test
    fun changingTheUnitOfAnAttributeRenamesTheArticles() = runBlocking<Unit> {
        article("auto")
        article("typed", name = "My rope", nameAuto = false)
        save("{rope_type} rope {diameter} {length}")

        val renamed = attributes.updateDefinition("diameter", "Diameter", "millimetres", null, 70)

        assertEquals(1, renamed)
        with(stored("auto")) {
            assertEquals("Static rope 10.5 millimetres 50 m", name)
            assertEquals(70, updatedAt)
        }
        assertEquals("My rope", stored("typed").name)
    }

    @Test
    fun sampleArticleIsTheLastEditedWithValues() = runBlocking<Unit> {
        article("old")
        articles.insert(
            Article(id = "new", name = "new", categoryId = "ropes", attributes = values("dynamic", null, null), createdAt = 1, updatedAt = 9),
        )
        articles.insert(Article(id = "bare", name = "bare", categoryId = "ropes", createdAt = 1, updatedAt = 20))

        assertEquals("new", attributes.observeSampleArticle("ropes").first()?.id)
        assertNull(attributes.observeSampleArticle("other").first())
    }
}

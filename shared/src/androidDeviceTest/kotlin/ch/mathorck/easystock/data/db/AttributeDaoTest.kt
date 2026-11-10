package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributeDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var attributes: AttributeDao
    private lateinit var categories: CategoryDao
    private var idCount = 0
    private val newId: () -> String = { "id${idCount++}" }

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
        attributes = db.attributeDao()
        categories = db.categoryDao()
        // User example: category "Ropes" with "Breaking strength" (N) and "Length" (m)
        categories.insert(Category(id = "ropes", name = "Ropes", createdAt = 0, updatedAt = 0))
        attributes.createDefinition("strength", "Breaking strength", AttributeDataType.NUMBER, "N", null, 0)
        attributes.createDefinition("length", "Length", AttributeDataType.NUMBER, "m", null, 0)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun linked(categoryId: String = "ropes") =
        attributes.observeCategoryAttributes(categoryId).first().map { it.definition.key to it.link.required }

    @Test
    fun keyIsDerivedFromLabelAndUnique() = runBlocking<Unit> {
        val defs = attributes.observeDefinitions().first().associate { it.id to it.key }
        assertEquals("breaking_strength", defs["strength"])
        assertEquals("length", defs["length"])

        val second = attributes.createDefinition("length2", "LENGTH", AttributeDataType.TEXT, null, null, 1)
        assertEquals("length_2", second.key)
    }

    @Test
    fun deletedKeyIsNotReused() = runBlocking<Unit> {
        attributes.softDeleteDefinition("length", 1)
        val again = attributes.createDefinition("length-new", "Length", AttributeDataType.NUMBER, "m", null, 2)
        assertEquals("length_2", again.key)
    }

    @Test
    fun updateKeepsKeyAndDataType() = runBlocking<Unit> {
        attributes.updateDefinition("length", "Rope length", "cm", listOf("ignored"), 5)
        val stored = attributes.getActiveDefinition("length")!!
        assertEquals("length", stored.key)
        assertEquals("Rope length", stored.label)
        assertEquals("cm", stored.unit)
        assertEquals(AttributeDataType.NUMBER, stored.dataType)
        assertNull(stored.options)
        assertEquals(5, stored.updatedAt)
    }

    @Test
    fun numberBecomesMeasureInItsUnit() = runBlocking<Unit> {
        attributes.updateDefinition("length", "Length", "kg", null, 5, AttributeDataType.MEASURE)
        val stored = attributes.getActiveDefinition("length")!!
        assertEquals(AttributeDataType.MEASURE, stored.dataType)
        assertEquals("m", stored.unit)
        assertEquals("length", stored.key)

        // The unit of a measure is locked, the type too
        attributes.updateDefinition("length", "Rope length", "mm", null, 6)
        assertEquals("m", attributes.getActiveDefinition("length")!!.unit)
        assertEquals("Rope length", attributes.getActiveDefinition("length")!!.label)
        assertFailsWith<IllegalArgumentException> { attributes.updateDefinition("length", "x", null, null, 7, AttributeDataType.NUMBER) }
        Unit
    }

    @Test
    fun measureNeedsAKnownUnit() = runBlocking<Unit> {
        assertEquals("kN", attributes.createDefinition("force", "Force", AttributeDataType.MEASURE, "KN", null, 0).unit)
        assertFailsWith<IllegalArgumentException> { attributes.createDefinition("v", "Voltage", AttributeDataType.MEASURE, "V", null, 0) }
        attributes.createDefinition("count", "Count", AttributeDataType.NUMBER, null, null, 0)
        assertFailsWith<IllegalArgumentException> { attributes.updateDefinition("count", "Count", "V", null, 1, AttributeDataType.MEASURE) }
        attributes.updateDefinition("count", "Count", "CM", null, 1, AttributeDataType.MEASURE)
        assertEquals("cm", attributes.getActiveDefinition("count")!!.unit)
        attributes.createDefinition("name", "Name", AttributeDataType.TEXT, null, null, 0)
        assertFailsWith<IllegalArgumentException> { attributes.updateDefinition("name", "Name", "m", null, 1, AttributeDataType.MEASURE) }
        Unit
    }

    @Test
    fun enumOptionsAreStored() = runBlocking<Unit> {
        attributes.createDefinition("color", "Color", AttributeDataType.ENUM, null, listOf("Red", "Blue"), 0)
        attributes.updateDefinition("color", "Color", null, listOf("Red", "Blue", "Green"), 1)
        assertEquals(listOf("Red", "Blue", "Green"), attributes.getActiveDefinition("color")!!.options)
    }

    @Test
    fun updateOfDeletedDefinitionFails() = runBlocking<Unit> {
        attributes.softDeleteDefinition("length", 1)
        assertFailsWith<AttributeNotFoundException> { attributes.updateDefinition("length", "x", null, null, 2) }
        Unit
    }

    @Test
    fun linksFollowTheGivenOrderAndRequired() = runBlocking<Unit> {
        attributes.setCategoryAttributes(
            "ropes",
            listOf(CategoryAttributeInput("strength", true), CategoryAttributeInput("length", false)),
            1, newId,
        )
        assertEquals(listOf("breaking_strength" to true, "length" to false), linked())

        attributes.setCategoryAttributes(
            "ropes",
            listOf(CategoryAttributeInput("length", true), CategoryAttributeInput("strength", false)),
            2, newId,
        )
        assertEquals(listOf("length" to true, "breaking_strength" to false), linked())
    }

    @Test
    fun removedLinkIsSoftDeletedAndCanBeAddedAgain() = runBlocking<Unit> {
        val both = listOf(CategoryAttributeInput("strength", true), CategoryAttributeInput("length", false))
        attributes.setCategoryAttributes("ropes", both, 1, newId)
        val linkId = attributes.observeCategoryAttributes("ropes").first().single { it.definition.id == "length" }.link.id

        attributes.setCategoryAttributes("ropes", listOf(CategoryAttributeInput("strength", true)), 2, newId)
        assertEquals(listOf("breaking_strength" to true), linked())

        // The unique (category_id, attribute_id) index covers the deleted row: it must be restored
        attributes.setCategoryAttributes("ropes", both, 3, newId)
        assertEquals(listOf("breaking_strength" to true, "length" to false), linked())
        val restored = attributes.observeCategoryAttributes("ropes").first().single { it.definition.id == "length" }.link
        assertEquals(linkId, restored.id)
        assertNull(restored.deletedAt)
        assertEquals(3, restored.updatedAt)
    }

    @Test
    fun unchangedLinkKeepsUpdatedAt() = runBlocking<Unit> {
        val list = listOf(CategoryAttributeInput("strength", true))
        attributes.setCategoryAttributes("ropes", list, 1, newId)
        attributes.setCategoryAttributes("ropes", list, 2, newId)
        assertEquals(1, attributes.observeCategoryAttributes("ropes").first().single().link.updatedAt)
    }

    @Test
    fun deletedDefinitionDisappearsFromCategories() = runBlocking<Unit> {
        attributes.setCategoryAttributes(
            "ropes",
            listOf(CategoryAttributeInput("strength", true), CategoryAttributeInput("length", false)),
            1, newId,
        )
        attributes.softDeleteDefinition("length", 2)
        assertEquals(listOf("breaking_strength" to true), linked())
        assertEquals(listOf("strength"), attributes.observeDefinitions().first().map { it.id })
    }

    @Test
    fun linkToDeletedDefinitionOrDuplicateIsRejected() = runBlocking<Unit> {
        attributes.softDeleteDefinition("length", 1)
        assertFailsWith<AttributeNotFoundException> {
            attributes.setCategoryAttributes("ropes", listOf(CategoryAttributeInput("length", false)), 2, newId)
        }
        assertFailsWith<IllegalArgumentException> {
            attributes.setCategoryAttributes(
                "ropes",
                listOf(CategoryAttributeInput("strength", false), CategoryAttributeInput("strength", true)),
                2, newId,
            )
        }
        assertTrue(linked().isEmpty())
    }

    @Test
    fun categoryCreateRenameAndDelete() = runBlocking<Unit> {
        categories.create(Category(id = "tools", name = "Tools", createdAt = 0, updatedAt = 0))
        assertFailsWith<DuplicateCategoryNameException> {
            categories.create(Category(id = "x", name = "ropes", createdAt = 0, updatedAt = 0))
        }
        categories.rename("tools", "TOOLS", 1)
        assertFailsWith<DuplicateCategoryNameException> { categories.rename("tools", "Ropes", 2) }

        categories.softDelete("tools", 3)
        assertEquals(listOf("Ropes"), categories.observeAll().first().map { it.name })
        assertFailsWith<DeletedCategoryNameException> {
            categories.create(Category(id = "y", name = "tools", createdAt = 0, updatedAt = 0))
        }
        assertFailsWith<CategoryNotFoundException> { categories.rename("tools", "Other", 4) }
        Unit
    }
}

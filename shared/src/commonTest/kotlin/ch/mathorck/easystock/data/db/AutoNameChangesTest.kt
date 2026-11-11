package ch.mathorck.easystock.data.db

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.test.Test
import kotlin.test.assertEquals

/** Names re-rendered when a category or its template changes (used by the category rename). */
@OptIn(ExperimentalSerializationApi::class)
class AutoNameChangesTest {
    private val diameter = AttributeDefinition(
        id = "d", key = "diametre", label = "Diamètre", dataType = AttributeDataType.NUMBER, unit = "mm", createdAt = 0, updatedAt = 0,
    )
    private val length = AttributeDefinition(
        id = "l", key = "longueure", label = "Longueur", dataType = AttributeDataType.NUMBER, unit = "m", createdAt = 0, updatedAt = 0,
    )
    private val definitions = listOf(diameter, length)
    private val values = JsonObject(mapOf("diametre" to JsonUnquotedLiteral("10"), "longueure" to JsonUnquotedLiteral("20")))

    private fun category(name: String, template: String? = "{category} {diametre} {longueure}") =
        Category(id = "c", name = name, nameTemplate = template, createdAt = 0, updatedAt = 0)

    private fun article(id: String, name: String, nameAuto: Boolean = true, deletedAt: Long? = null, attributes: JsonObject? = values) =
        Article(id = id, name = name, nameAuto = nameAuto, categoryId = "c", attributes = attributes, createdAt = 0, updatedAt = 0, deletedAt = deletedAt)

    @Test
    fun renamedCategoryRenamesTemplateNamesOnly() {
        val articles = listOf(
            article("auto", "Ropes 10 mm 20 m"),
            article("typed", "My favourite rope", nameAuto = false),
            article("deleted", "Ropes 10 mm 20 m", deletedAt = 3),
        )
        assertEquals(mapOf("auto" to "Cordes 10 mm 20 m"), autoNameChanges(category("Cordes"), definitions, articles))
    }

    @Test
    fun unchangedNamesAreLeftOut() {
        assertEquals(emptyMap(), autoNameChanges(category("Cordes"), definitions, listOf(article("same", "Cordes 10 mm 20 m"))))
    }

    @Test
    fun emptyNameKeepsTheOldOne() {
        val empty = listOf(article("empty", "Old", attributes = null))
        assertEquals(emptyMap(), autoNameChanges(category("Cordes", "{diametre}"), definitions, empty))
        // Without values only the category name is left
        assertEquals(mapOf("empty" to "Cordes"), autoNameChanges(category("Cordes"), definitions, empty))
    }

    @Test
    fun noTemplateNoChange() {
        assertEquals(emptyMap(), autoNameChanges(category("Cordes", null), definitions, listOf(article("auto", "x"))))
    }
}

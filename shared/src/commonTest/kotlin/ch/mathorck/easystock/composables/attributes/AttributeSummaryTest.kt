package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.viewmodels.filterByQuery
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class AttributeSummaryTest {
    private fun def(key: String, type: AttributeDataType, label: String = key, unit: String? = null) =
        AttributeDefinition(id = "id_$key", key = key, label = label, dataType = type, unit = unit, createdAt = 0, updatedAt = 0)

    private fun link(key: String, order: Int, category: String = "cat") =
        CategoryAttribute(id = "l_$key", categoryId = category, attributeId = "id_$key", sortOrder = order, createdAt = 0, updatedAt = 0)

    private fun num(text: String) = JsonUnquotedLiteral(text)

    private val type = def("type", AttributeDataType.ENUM, "Type")
    private val diameter = def("diameter", AttributeDataType.NUMBER, "Diameter", "mm")
    private val length = def("length", AttributeDataType.NUMBER, "Length", "m")
    private val strength = def("strength", AttributeDataType.NUMBER, "Strength", "kN")
    private val waterproof = def("waterproof", AttributeDataType.BOOL, "Waterproof")
    private val bought = def("bought", AttributeDataType.DATE, "Bought")
    private val note = def("note", AttributeDataType.TEXT, "Note")
    private val all = listOf(type, diameter, length, strength, waterproof, bought, note)

    private fun article(attributes: JsonObject?, category: String? = "cat") =
        Article(id = "a", name = "Rope", categoryId = category, attributes = attributes, createdAt = 0, updatedAt = 0)

    @Test
    fun formatsExampleInCategoryOrder() {
        val catalog = AttributeCatalog(all, listOf(link("type", 0), link("diameter", 1), link("length", 2), link("strength", 3)))
        val a = article(
            JsonObject(
                mapOf(
                    "strength" to num("22"), "length" to num("50"), "type" to JsonPrimitive("static"), "diameter" to num("10.50"),
                ),
            ),
        )
        assertEquals("static · 10.5 mm · 50 m · 22 kN", catalog.format(a).summaryLine())
    }

    @Test
    fun unlinkedAttributesGoAfterByLabel() {
        val catalog = AttributeCatalog(all, listOf(link("length", 0)))
        val a = article(JsonObject(mapOf("type" to JsonPrimitive("x"), "length" to num("5"), "diameter" to num("2"))))
        assertEquals("5 m · 2 mm · x", catalog.format(a).summaryLine())
    }

    @Test
    fun linksOfAnotherCategoryAreIgnoredAndNoCategoryWorks() {
        val catalog = AttributeCatalog(all, listOf(link("type", 0, "other"), link("length", 1, "other")))
        val a = article(JsonObject(mapOf("type" to JsonPrimitive("x"), "length" to num("5"))), category = null)
        assertEquals("5 m · x", catalog.format(a).summaryLine())
    }

    @Test
    fun skipsEmptyValuesFalseBoolAndUnknownKeys() {
        val catalog = AttributeCatalog(all, emptyList())
        val a = article(
            JsonObject(
                mapOf(
                    "note" to JsonPrimitive("  "), "waterproof" to JsonPrimitive(false),
                    "gone" to JsonPrimitive("old"), "bought" to JsonPrimitive("not a date"),
                ),
            ),
        )
        assertEquals(emptyList(), catalog.format(a))
        assertEquals(emptyList(), catalog.format(article(null)))
    }

    @Test
    fun boolShowsLabelWhenTrue() {
        assertEquals(AttributeText("Waterproof", "Waterproof"), formatAttributeValue(waterproof, JsonPrimitive(true)))
        assertNull(formatAttributeValue(waterproof, JsonPrimitive("true")))
    }

    @Test
    fun dateIsIso() {
        assertEquals("2026-03-05", formatAttributeValue(bought, JsonPrimitive("2026-03-05"))?.text)
    }

    @Test
    fun numbersHaveNoTrailingZeros() {
        assertEquals("10.5", formatAttributeNumber("10.50"))
        assertEquals("50", formatAttributeNumber("50.0"))
        assertEquals("50", formatAttributeNumber("50"))
        assertEquals("100", formatAttributeNumber("100"))
        assertEquals("0", formatAttributeNumber("0.00"))
        assertEquals(AttributeText("50", "50 m"), formatAttributeValue(length, num("50"))?.copy(measure = null))
        assertEquals(AttributeText("3", "3"), formatAttributeValue(def("n", AttributeDataType.NUMBER), num("3")))
    }

    @Test
    fun searchMatchesRawAndWithUnitIgnoringCase() {
        val catalog = AttributeCatalog(all, emptyList())
        val rope = ArticleStock(article(JsonObject(mapOf("length" to num("50"), "type" to JsonPrimitive("Static")))), 0)
        val plain = ArticleStock(Article(id = "b", name = "Nail", createdAt = 0, updatedAt = 0), 0)
        val items = listOf(rope, plain)
        val attrs = { s: ArticleStock -> catalog.format(s.article) }
        fun ids(q: String) = items.filterByQuery(q, attrs).map { it.article.id }
        assertEquals(listOf("a"), ids("50"))
        assertEquals(listOf("a"), ids("50 m"))
        assertEquals(listOf("a"), ids("STATIC"))
        assertEquals(listOf("a", "b"), ids(""))
        assertEquals(listOf("b"), ids("nail"))
        assertTrue(ids("60 m").isEmpty())
    }
}

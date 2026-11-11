package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterStockTest {
    private fun item(
        id: String,
        name: String,
        categoryId: String?,
        stock: Long,
        threshold: Long?,
        tracking: Tracking = Tracking.QUANTITY,
        tag: String? = null,
        color: JsonPrimitive? = null,
    ) = ArticleStock(
        Article(
            id = id, sku = "SKU-$id", name = name, categoryId = categoryId, thresholdMilli = threshold,
            tracking = tracking, barcode = tag,
            attributes = color?.let { buildJsonObject { put("color", it) } },
            createdAt = 0, updatedAt = 0,
        ),
        stock,
    )

    private val items = listOf(
        item("1", "Wood screw", "hw", stock = 1_000, threshold = 5_000, tag = "04A1", color = JsonPrimitive("red")), // low
        item("2", "Bolt M8", "hw", stock = 9_000, threshold = 5_000, color = JsonPrimitive("  ")), // ok, blank color
        item("3", "Duct tape", "tape", stock = 0, threshold = 0, tag = "04B2"), // low (at threshold), out
        item("4", "Cable tie", null, stock = -500, threshold = null, color = JsonPrimitive("black")), // never low, out (negative)
        item("5", "Drill", null, stock = 0, threshold = 1_000, tracking = Tracking.SERIAL, tag = "04C3"), // tool: never low, out
        item("6", "Hammer", "hw", stock = 2_000, threshold = null, tracking = Tracking.SERIAL),
    )

    private val color = AttributeDefinition(
        id = "attr_color", key = "color", label = "Color", dataType = AttributeDataType.TEXT, createdAt = 0, updatedAt = 0,
    )

    private fun ids(query: String = "", filters: StockFilters = StockFilters(), attribute: AttributeDefinition? = null) =
        items.filterStock(query, filters, attribute).map { it.article.id }

    @Test
    fun noFilterKeepsAll() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), ids())
    }

    @Test
    fun categoryKeepsOnlyThatCategory() {
        assertEquals(listOf("1", "2", "6"), ids(filters = StockFilters(categoryId = "hw")))
    }

    @Test
    fun unknownCategoryGivesEmptyList() {
        assertEquals(emptyList(), ids(filters = StockFilters(categoryId = "none")))
    }

    @Test
    fun lowStockKeepsOnlyLowArticles() {
        assertEquals(listOf("1", "3"), ids(filters = StockFilters(stockLevel = StockLevelFilter.LOW)))
    }

    @Test
    fun outOfStockKeepsZeroAndNegativeStock() {
        assertEquals(listOf("3", "4", "5"), ids(filters = StockFilters(stockLevel = StockLevelFilter.OUT)))
    }

    @Test
    fun typeKeepsConsumablesOrTools() {
        assertEquals(listOf("1", "2", "3", "4"), ids(filters = StockFilters(tracking = Tracking.QUANTITY)))
        assertEquals(listOf("5", "6"), ids(filters = StockFilters(tracking = Tracking.SERIAL)))
    }

    @Test
    fun tagFilterKeepsTaggedOrUntagged() {
        assertEquals(listOf("1", "3", "5"), ids(filters = StockFilters(tag = TagFilter.TAGGED)))
        assertEquals(listOf("2", "4", "6"), ids(filters = StockFilters(tag = TagFilter.UNTAGGED)))
    }

    @Test
    fun attributeFilterKeepsArticlesWithAValue() {
        // Blank value (2) and missing value count as no value
        assertEquals(listOf("1", "4"), ids(filters = StockFilters(attributeId = color.id), attribute = color))
    }

    @Test
    fun falseBoolIsNoValue() {
        val bool = color.copy(dataType = AttributeDataType.BOOL)
        val article = items[0].article
        assertTrue(article.copy(attributes = buildJsonObject { put("color", JsonPrimitive(true)) }).hasAttribute(bool))
        assertFalse(article.copy(attributes = buildJsonObject { put("color", JsonPrimitive(false)) }).hasAttribute(bool))
        assertFalse(article.copy(attributes = null).hasAttribute(bool))
    }

    @Test
    fun filtersCombine() {
        assertEquals(listOf("1"), ids(filters = StockFilters(categoryId = "hw", stockLevel = StockLevelFilter.LOW)))
        assertEquals(listOf("5"), ids(filters = StockFilters(tracking = Tracking.SERIAL, stockLevel = StockLevelFilter.OUT)))
        assertEquals(listOf("3"), ids(filters = StockFilters(tag = TagFilter.TAGGED, stockLevel = StockLevelFilter.LOW, tracking = Tracking.QUANTITY, categoryId = "tape")))
        assertEquals(listOf("4"), ids(filters = StockFilters(tag = TagFilter.UNTAGGED, attributeId = color.id), attribute = color))
    }

    @Test
    fun queryAndFiltersCombine() {
        assertEquals(listOf("2"), ids(query = "bolt", filters = StockFilters(categoryId = "hw")))
        assertEquals(emptyList(), ids(query = "tape", filters = StockFilters(categoryId = "hw")))
        assertEquals(listOf("1"), ids(query = "sku", filters = StockFilters(categoryId = "hw", stockLevel = StockLevelFilter.LOW)))
        assertEquals(emptyList(), ids(query = "bolt", filters = StockFilters(categoryId = "hw", stockLevel = StockLevelFilter.LOW)))
        assertEquals(listOf("5"), ids(query = "dri", filters = StockFilters(tag = TagFilter.TAGGED)))
        assertEquals(emptyList(), ids(query = "dri", filters = StockFilters(tracking = Tracking.QUANTITY)))
    }

    @Test
    fun choosingToolsDropsLowStock() {
        val low = StockFilters(stockLevel = StockLevelFilter.LOW)
        assertNull(low.withTracking(Tracking.SERIAL).stockLevel)
        assertEquals(StockLevelFilter.LOW, low.withTracking(Tracking.QUANTITY).stockLevel)
        val out = StockFilters(stockLevel = StockLevelFilter.OUT)
        assertEquals(StockLevelFilter.OUT, out.withTracking(Tracking.SERIAL).stockLevel)
    }

    @Test
    fun stockLevelToggles() {
        val low = StockFilters().toggleStockLevel(StockLevelFilter.LOW)
        assertEquals(StockLevelFilter.LOW, low.stockLevel)
        assertEquals(StockLevelFilter.OUT, low.toggleStockLevel(StockLevelFilter.OUT).stockLevel)
        assertNull(low.toggleStockLevel(StockLevelFilter.LOW).stockLevel)
    }

    @Test
    fun staleIdsAreDropped() {
        val filters = StockFilters(locationId = "gone", categoryId = "hw", stockLevel = StockLevelFilter.LOW, attributeId = "deleted")

        assertEquals(
            StockFilters(locationId = null, categoryId = "hw", stockLevel = StockLevelFilter.LOW, attributeId = null),
            filters.withoutStale(locationIds = setOf("shelf"), categoryIds = setOf("hw"), attributeIds = setOf(color.id)),
        )
        assertEquals(
            StockFilters(attributeId = color.id),
            StockFilters(attributeId = color.id).withoutStale(emptySet(), emptySet(), setOf(color.id)),
        )
    }

    @Test
    fun isActiveWhenAnyFilterIsSet() {
        assertFalse(StockFilters().isActive)
        assertTrue(StockFilters(locationId = "shelf").isActive)
        assertTrue(StockFilters(categoryId = "hw").isActive)
        assertTrue(StockFilters(stockLevel = StockLevelFilter.OUT).isActive)
        assertTrue(StockFilters(tracking = Tracking.SERIAL).isActive)
        assertTrue(StockFilters(tag = TagFilter.UNTAGGED).isActive)
        assertTrue(StockFilters(attributeId = "attr").isActive)
    }
}

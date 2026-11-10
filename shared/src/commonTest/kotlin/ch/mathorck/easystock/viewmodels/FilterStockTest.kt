package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterStockTest {
    private fun item(id: String, name: String, categoryId: String?, stock: Long, threshold: Long?) =
        ArticleStock(
            Article(id = id, sku = "SKU-$id", name = name, categoryId = categoryId, thresholdMilli = threshold, createdAt = 0, updatedAt = 0),
            stock,
        )

    private val items = listOf(
        item("1", "Wood screw", "hw", stock = 1_000, threshold = 5_000), // low
        item("2", "Bolt M8", "hw", stock = 9_000, threshold = 5_000), // ok
        item("3", "Duct tape", "tape", stock = 0, threshold = 0), // low (at threshold)
        item("4", "Cable tie", null, stock = 0, threshold = null), // no threshold, never low
    )

    private fun ids(query: String = "", categoryId: String? = null, lowStockOnly: Boolean = false) =
        items.filterStock(query, categoryId, lowStockOnly).map { it.article.id }

    @Test
    fun noFilterKeepsAll() {
        assertEquals(listOf("1", "2", "3", "4"), ids())
    }

    @Test
    fun categoryKeepsOnlyThatCategory() {
        assertEquals(listOf("1", "2"), ids(categoryId = "hw"))
    }

    @Test
    fun unknownCategoryGivesEmptyList() {
        assertEquals(emptyList(), ids(categoryId = "none"))
    }

    @Test
    fun lowStockKeepsOnlyLowArticles() {
        assertEquals(listOf("1", "3"), ids(lowStockOnly = true))
    }

    @Test
    fun lowStockAndCategoryCombine() {
        assertEquals(listOf("1"), ids(categoryId = "hw", lowStockOnly = true))
    }

    @Test
    fun queryAndCategoryCombine() {
        assertEquals(listOf("2"), ids(query = "bolt", categoryId = "hw"))
        assertEquals(emptyList(), ids(query = "tape", categoryId = "hw"))
    }

    @Test
    fun queryCategoryAndLowStockCombine() {
        assertEquals(listOf("1"), ids(query = "sku", categoryId = "hw", lowStockOnly = true))
        assertEquals(emptyList(), ids(query = "bolt", categoryId = "hw", lowStockOnly = true))
    }

    @Test
    fun staleIdsAreDropped() {
        val filters = StockFilters(locationId = "gone", categoryId = "hw", lowStockOnly = true)

        assertEquals(
            StockFilters(locationId = null, categoryId = "hw", lowStockOnly = true),
            filters.withoutStale(locationIds = setOf("shelf"), categoryIds = setOf("hw")),
        )
    }

    @Test
    fun isActiveWhenAnyFilterIsSet() {
        assertFalse(StockFilters().isActive)
        assertTrue(StockFilters(locationId = "shelf").isActive)
        assertTrue(StockFilters(categoryId = "hw").isActive)
        assertTrue(StockFilters(lowStockOnly = true).isActive)
    }
}

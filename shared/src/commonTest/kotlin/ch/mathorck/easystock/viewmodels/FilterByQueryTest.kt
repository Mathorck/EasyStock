package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterByQueryTest {
    private val items = listOf(
        ArticleStock(Article(id = "1", sku = "SCR-4", name = "Wood screw", createdAt = 0, updatedAt = 0), 0),
        ArticleStock(Article(id = "2", sku = "BLT-8", name = "Bolt M8", createdAt = 0, updatedAt = 0), 0),
    )

    private fun ids(query: String) = items.filterByQuery(query).map { it.article.id }

    @Test
    fun blankQueryKeepsAll() {
        assertEquals(listOf("1", "2"), ids("  "))
    }

    @Test
    fun matchesNameIgnoringCase() {
        assertEquals(listOf("1"), ids("SCREW"))
    }

    @Test
    fun matchesSku() {
        assertEquals(listOf("2"), ids("blt"))
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(listOf("2"), ids(" bolt "))
    }

    @Test
    fun noMatchGivesEmptyList() {
        assertEquals(emptyList(), ids("nail"))
    }
}

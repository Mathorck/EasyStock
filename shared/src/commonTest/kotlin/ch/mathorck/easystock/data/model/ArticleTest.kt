package ch.mathorck.easystock.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleTest {
    private val article = Article(
        id = "a1",
        sku = "SKU-1",
        name = "Screw M4",
        barcode = "04:A2:3B:12:7C:80:00",
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    @Test
    fun softDeleteFreesTheNfcTag() {
        val deleted = article.softDeleted(now = 2_000)

        assertNull(deleted.barcode)
        assertEquals(2_000, deleted.deletedAt)
        assertEquals(2_000, deleted.updatedAt)
    }

    @Test
    fun softDeleteKeepsTheRestOfTheArticle() {
        val deleted = article.softDeleted(now = 2_000)

        assertEquals(article.copy(barcode = null, deletedAt = 2_000, updatedAt = 2_000), deleted)
        assertEquals(1_000, deleted.createdAt)
        assertEquals("SKU-1", deleted.sku)
    }
}

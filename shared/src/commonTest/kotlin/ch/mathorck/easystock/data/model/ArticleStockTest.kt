package ch.mathorck.easystock.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleStockTest {
    private fun stock(stockMilli: Long, thresholdMilli: Long?) = ArticleStock(
        article = Article(id = "a1", sku = "SKU-1", name = "Screw", thresholdMilli = thresholdMilli, createdAt = 0, updatedAt = 0),
        stockMilli = stockMilli,
    )

    @Test
    fun noThresholdIsNeverLow() {
        assertFalse(stock(0, null).isLowStock)
    }

    @Test
    fun belowThresholdIsLow() {
        assertTrue(stock(4_000, 5_000).isLowStock)
    }

    @Test
    fun atThresholdIsLow() {
        assertTrue(stock(5_000, 5_000).isLowStock)
    }

    @Test
    fun aboveThresholdIsNotLow() {
        assertFalse(stock(5_001, 5_000).isLowStock)
    }

    @Test
    fun negativeStockIsLow() {
        assertTrue(stock(-1_000, 0).isLowStock)
    }
}

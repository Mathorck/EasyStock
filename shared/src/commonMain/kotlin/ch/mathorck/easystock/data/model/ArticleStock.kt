package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** An article with its current stock (sum of its movements), in thousandths of its unit. */
data class ArticleStock(
    @Embedded val article: Article,
    @ColumnInfo(name = "stock") val stockMilli: Long,
) {
    /** See [Article.isLowAt]. */
    val isLowStock: Boolean
        get() = article.isLowAt(stockMilli)
}

/**
 * True when a threshold is set and [stockMilli] is at or below it. Serial-tracked articles (tools) have no
 * low-stock alert: a threshold left on an old row is ignored.
 */
fun Article.isLowAt(stockMilli: Long): Boolean =
    tracking != Tracking.SERIAL && thresholdMilli?.let { stockMilli <= it } ?: false

package ch.mathorck.easystock.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** An article with its current stock (sum of its movements), in thousandths of its unit. */
data class ArticleStock(
    @Embedded val article: Article,
    @ColumnInfo(name = "stock") val stockMilli: Long,
) {
    /** True when a threshold is set and the stock is at or below it. */
    val isLowStock: Boolean
        get() = article.thresholdMilli?.let { stockMilli <= it } ?: false
}

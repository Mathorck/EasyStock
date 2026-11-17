package ch.mathorck.easystock.data.dao.location

import androidx.room.ColumnInfo
import androidx.room.Embedded
import ch.mathorck.easystock.data.model.article.Article

/** Stock (sum of the movements) of one article at one location. */
data class LocationArticleStock(
    @Embedded val article: Article,
    @ColumnInfo(name = "stock_location_id") val locationId: String,
    @ColumnInfo(name = "stock") val stockMilli: Long,
)

package ch.mathorck.easystock.services.review

import androidx.room.ColumnInfo

// ---------- Lines from the stock ----------

/**
 * Stock found in the scope: an article at a location ([itemId] null, quantity tracking),
 * or one unit at the location where its movements put it.
 */
data class ReviewPosition(
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "quantity") val quantityMilli: Long,
)

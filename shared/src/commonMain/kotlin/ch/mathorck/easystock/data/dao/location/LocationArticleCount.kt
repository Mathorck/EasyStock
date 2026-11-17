package ch.mathorck.easystock.data.dao.location

import androidx.room.ColumnInfo

/** Number of articles with a non-zero stock directly at [locationId] (descendants not included). */
data class LocationArticleCount(
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "article_count") val articleCount: Int,
)

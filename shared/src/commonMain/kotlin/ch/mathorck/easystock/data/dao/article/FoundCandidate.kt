package ch.mathorck.easystock.data.dao.article

import androidx.room.ColumnInfo
import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.article.Tracking

/** Article or unit offered by the "found here" search. */
data class FoundCandidate(
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "article_name") val articleName: String,
    val tracking: Tracking,
    val unit: String,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "item_status") val itemStatus: ItemStatus?,
    @ColumnInfo(name = "location_id") val locationId: String?,
)

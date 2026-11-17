package ch.mathorck.easystock.data.model.alerts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A low-stock alert received from the server. Never created on the phone. No foreign key: it may arrive before its article. */
@Entity(
    tableName = "low_stock_alert",
    indices = [Index(value = ["article_id"]), Index(value = ["notified"])],
)
data class LowStockAlert(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "crossed_at") val crossedAt: Long,
    /** Stock at that time, in thousandths of the article unit. */
    @ColumnInfo(name = "stock") val stockMilli: Long,
    @ColumnInfo(defaultValue = "0") val notified: Boolean = false,
)

package ch.mathorck.easystock.data.model.alerts

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A date-expiry notification already shown (v13), so it is shown once per date. App only: never synced,
 * never in a backup. [targetId] is an article or item id, [dateValue] the notified date ("YYYY-MM-DD"):
 * a changed date notifies again.
 */
@Entity(tableName = "date_alert_notice", primaryKeys = ["target_id", "attribute_key", "date_value"])
data class DateAlertNotice(
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "attribute_key") val attributeKey: String,
    @ColumnInfo(name = "date_value") val dateValue: String,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long,
)

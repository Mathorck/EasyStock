package ch.mathorck.easystock.data.dao.alerts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ch.mathorck.easystock.data.model.alerts.DateAlertNotice

/** Date-expiry notifications already shown (`date_alert_notice`, app only), to show each date once. */
@Dao
abstract class DateAlertNoticeDao {
    /** Records a notice. Returns true when it is new, false when this date was already notified. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(notice: DateAlertNotice): Long

    suspend fun markNotified(notice: DateAlertNotice): Boolean = insertIfAbsent(notice) != -1L

    @Query("SELECT COUNT(*) > 0 FROM date_alert_notice WHERE target_id = :targetId AND attribute_key = :attributeKey AND date_value = :dateValue")
    abstract suspend fun isNotified(targetId: String, attributeKey: String, dateValue: String): Boolean

    /** Every notice, to skip the dates already notified. */
    @Query("SELECT * FROM date_alert_notice")
    abstract suspend fun all(): List<DateAlertNotice>

    /** Records the notices in one transaction: all or none. */
    @Transaction
    open suspend fun markAllNotified(notices: List<DateAlertNotice>) {
        notices.forEach { insertIfAbsent(it) }
    }

    @Query("DELETE FROM date_alert_notice WHERE target_id = :targetId AND attribute_key = :attributeKey AND date_value = :dateValue")
    protected abstract suspend fun deleteNotice(targetId: String, attributeKey: String, dateValue: String)

    /** Deletes the notices of (target id, attribute key, date value) [keys], in one transaction. */
    @Transaction
    open suspend fun deleteNotices(keys: Collection<Triple<String, String, String>>) {
        keys.forEach { (target, key, value) -> deleteNotice(target, key, value) }
    }
}

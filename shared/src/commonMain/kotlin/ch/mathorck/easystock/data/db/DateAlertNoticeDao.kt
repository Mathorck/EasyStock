package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ch.mathorck.easystock.data.model.DateAlertNotice

/** Date-expiry notifications already shown (`date_alert_notice`, app only), to show each date once. */
@Dao
abstract class DateAlertNoticeDao {
    /** Records a notice. Returns true when it is new, false when this date was already notified. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(notice: DateAlertNotice): Long

    suspend fun markNotified(notice: DateAlertNotice): Boolean = insertIfAbsent(notice) != -1L

    @Query("SELECT COUNT(*) > 0 FROM date_alert_notice WHERE target_id = :targetId AND attribute_key = :attributeKey AND date_value = :dateValue")
    abstract suspend fun isNotified(targetId: String, attributeKey: String, dateValue: String): Boolean
}

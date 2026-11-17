package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.model.DateAlertNotice
import ch.mathorck.easystock.data.model.PastDate
import ch.mathorck.easystock.viewmodels.currentDateValues
import ch.mathorck.easystock.viewmodels.pastDates
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** [DateAlertStore] on the app database. */
class RoomDateAlertStore(private val db: AppDatabase) : DateAlertStore {
    override suspend fun pastDates(today: LocalDate): List<PastDate> = pastDates(
        db.attributeDao().observeDefinitions().first(),
        db.dateAlertDao().observeArticles().first(),
        db.dateAlertDao().observeItems().first(),
        today,
    )

    override suspend fun notified(): Set<Triple<String, String, String>> =
        db.dateAlertNoticeDao().all().mapTo(HashSet()) { Triple(it.targetId, it.attributeKey, it.dateValue) }

    override suspend fun currentDates(): Map<Pair<String, String>, String> =
        currentDateValues(db.dateAlertDao().observeArticles().first(), db.dateAlertDao().observeItems().first())

    override suspend fun markNotified(dates: List<PastDate>, now: Long) {
        db.dateAlertNoticeDao().markAllNotified(dates.map { DateAlertNotice(it.targetId, it.attributeKey, it.dateValue, now) })
    }

    override suspend fun prune(stale: Set<Triple<String, String, String>>) = db.dateAlertNoticeDao().deleteNotices(stale)
}

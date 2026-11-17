package ch.mathorck.easystock.services.alerts

import ch.mathorck.easystock.data.model.attributes.PastDate
import kotlinx.datetime.LocalDate

/** The rows the date notifier reads and marks (`date_alert_notice`, article and item dates). */
interface DateAlertStore {
    /** The past dates of [today], notify or not (see [ch.mathorck.easystock.viewmodels.alerts.pastDates]). */
    suspend fun pastDates(today: LocalDate): List<PastDate>

    /** Dates already notified: (target id, attribute key, date value). */
    suspend fun notified(): Set<Triple<String, String, String>>

    /** Date values of live, not archived articles and items (see [ch.mathorck.easystock.viewmodels.alerts.currentDateValues]). */
    suspend fun currentDates(): Map<Pair<String, String>, String>

    suspend fun markNotified(dates: List<PastDate>, now: Long)

    /** Forgets the [stale] notices (see [ch.mathorck.easystock.viewmodels.alerts.staleNotices]). */
    suspend fun prune(stale: Set<Triple<String, String, String>>)
}

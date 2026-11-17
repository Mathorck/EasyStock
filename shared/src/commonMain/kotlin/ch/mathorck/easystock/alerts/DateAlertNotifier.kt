package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.viewmodels.datesToNotify
import ch.mathorck.easystock.viewmodels.localToday
import ch.mathorck.easystock.viewmodels.staleNotices
import kotlin.time.Clock

/**
 * The daily local check: notifies each past date of a definition with `notify_when_past` once (see [datesToNotify]),
 * and forgets the notices of deleted, archived or changed dates. Works in standalone and server mode: it reads the local database only.
 * A date is marked as notified only when the notification was really shown.
 */
class DateAlertNotifier(
    private val store: DateAlertStore,
    private val notifications: DateAlertNotifications,
    private val clock: Clock = Clock.System,
) {
    suspend fun check() {
        val past = store.pastDates(localToday(clock))
        val notified = store.notified()
        // A deleted, archived or changed date is forgotten; a flag turned off is not, so no repeat
        val stale = staleNotices(store.currentDates(), notified)
        if (stale.isNotEmpty()) store.prune(stale)
        val due = datesToNotify(past, notified - stale)
        if (due.isEmpty()) return
        if (notifications.show(due)) store.markNotified(due, clock.now().toEpochMilliseconds())
    }
}

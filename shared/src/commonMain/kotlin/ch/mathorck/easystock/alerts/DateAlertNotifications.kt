package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.data.model.PastDate

/** The phone's notifications for past dates (Android: the "Dates passed" channel, apart from the low-stock one). */
interface DateAlertNotifications {
    /**
     * Shows one notification for [dates]. Returns false when nothing could be shown (notifications refused),
     * so the dates are notified later instead of being lost.
     */
    suspend fun show(dates: List<PastDate>): Boolean
}

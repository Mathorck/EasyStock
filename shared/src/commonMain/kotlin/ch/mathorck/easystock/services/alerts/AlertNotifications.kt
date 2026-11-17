package ch.mathorck.easystock.services.alerts

/** The phone's notifications (Android: the "Low stock" channel). Fails silently without the permission. */
interface AlertNotifications {
    /** Creates the "Low stock" channel, so the user can turn it off before the first alert. */
    suspend fun createChannel()

    /** The low-stock notifications still shown. */
    suspend fun shown(): List<ShownAlert>

    /** Shows or replaces the notification of [notice] (key: its article id). Tapping it opens the article. */
    suspend fun show(notice: AlertNotice)

    /** Shows or replaces the grouped notification ([GROUP_KEY]). Tapping it opens the Stock page on "low stock". */
    suspend fun showGroup(notices: List<AlertNotice>)

    fun cancel(key: String)

    /** Removes every low-stock notification (the phone left the server). */
    fun cancelAll()
}

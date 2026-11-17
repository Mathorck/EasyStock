package ch.mathorck.easystock.services.alerts

/** The permission to post notifications (Android 13+). */
interface NotificationPermission {
    /** True when the system dialog can still be shown: Android 13+ and not granted. */
    val canRequest: Boolean

    /** Shows the system dialog. A denial changes nothing else: the alerts are still marked as seen. */
    fun request()
}

package ch.mathorck.easystock.alerts

import androidx.compose.runtime.Composable

/** The low-stock notifications of the phone. Null where server mode is not available (iOS). */
@Composable
expect fun rememberAlertNotifications(): AlertNotifications?

/** The permission to post notifications (Android 13+). */
interface NotificationPermission {
    /** True when the system dialog can still be shown: Android 13+ and not granted. */
    val canRequest: Boolean

    /** Shows the system dialog. A denial changes nothing else: the alerts are still marked as seen. */
    fun request()
}

/** Null where nothing must be asked (before Android 13, iOS). */
@Composable
expect fun rememberNotificationPermission(): NotificationPermission?

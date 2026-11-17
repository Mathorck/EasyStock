package ch.mathorck.easystock.services.alerts

import androidx.compose.runtime.Composable

/** The low-stock notifications of the phone. Null where server mode is not available (iOS). */
@Composable
expect fun rememberAlertNotifications(): AlertNotifications?

/** Null where nothing must be asked (before Android 13, iOS). */
@Composable
expect fun rememberNotificationPermission(): NotificationPermission?

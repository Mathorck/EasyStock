package ch.mathorck.easystock.alerts

import androidx.compose.runtime.Composable

// TODO(server mode on iOS): only Android is delivered
@Composable
actual fun rememberAlertNotifications(): AlertNotifications? = null

@Composable
actual fun rememberNotificationPermission(): NotificationPermission? = null

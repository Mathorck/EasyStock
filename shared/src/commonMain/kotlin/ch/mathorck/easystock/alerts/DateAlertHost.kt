package ch.mathorck.easystock.alerts

import androidx.compose.runtime.Composable

/**
 * Keeps the daily past-date check scheduled, in standalone and server mode (it reads the local database only).
 * Where there is no background job (iOS) it does nothing.
 */
@Composable
expect fun DateAlertHost()

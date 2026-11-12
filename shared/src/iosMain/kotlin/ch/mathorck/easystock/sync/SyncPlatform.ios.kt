package ch.mathorck.easystock.sync

import androidx.compose.runtime.Composable

// TODO(server mode on iOS): only Android is delivered
@Composable
actual fun rememberSyncEngine(): SyncEngine? = null

@Composable
actual fun rememberBackgroundSyncScheduler(): BackgroundSyncScheduler? = null

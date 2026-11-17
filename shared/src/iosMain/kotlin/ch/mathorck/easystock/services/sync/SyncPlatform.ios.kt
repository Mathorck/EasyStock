package ch.mathorck.easystock.services.sync

import androidx.compose.runtime.Composable

// TODO(server mode on iOS): only Android is delivered
@Composable
actual fun rememberSyncEngine(): SyncEngine? = null

@Composable
actual fun rememberAutoBackupSharer(): (String) -> Unit = {}

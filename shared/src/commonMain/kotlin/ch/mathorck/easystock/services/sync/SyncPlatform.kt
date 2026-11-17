package ch.mathorck.easystock.services.sync

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The process-wide sync engine. Null where server mode is not available (iOS). Builds nothing that
 * reaches the network: a cycle reads `sync_state` first and stops in standalone mode.
 */
@Composable
expect fun rememberSyncEngine(): SyncEngine?

/** Sends the automatic backup file of that name ([AutoBackups]) to another app chosen by the user. */
@Composable
expect fun rememberAutoBackupSharer(): (fileName: String) -> Unit


package ch.mathorck.easystock.sync

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

/** Counts the photo downloads, so a placeholder shown for a photo still downloading loads it once it is there. */
object SyncImages {
    private val _downloads = MutableStateFlow(0)
    val downloads: StateFlow<Int> = _downloads.asStateFlow()

    fun downloaded() = _downloads.update { it + 1 }
}

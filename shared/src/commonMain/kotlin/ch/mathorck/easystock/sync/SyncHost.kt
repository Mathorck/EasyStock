package ch.mathorck.easystock.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import ch.mathorck.easystock.data.db.rememberAppDatabase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlin.time.Duration.Companion.seconds

/** Wait after the last local change before a sync, so a burst of edits goes in one push. */
val OUTBOX_DEBOUNCE = 3.seconds

/**
 * The sync triggers while connected ([connected]: a `sync_state` row exists): each time the app comes
 * to the front (the first one right after joining runs the full pull), and [OUTBOX_DEBOUNCE] after
 * the last local change. Composes nothing in standalone mode: no engine, no network.
 */
@OptIn(FlowPreview::class)
@Composable
fun SyncHost(connected: Boolean) {
    if (!connected) return
    val engine = rememberSyncEngine() ?: return
    val db = rememberAppDatabase()
    val dao = remember(db) { db.syncDao() }
    LifecycleResumeEffect(engine) {
        engine.requestSync()
        onPauseOrDispose {}
    }
    LaunchedEffect(engine, dao) {
        dao.observeOutboxCount()
            .filter { it > 0 }
            .debounce(OUTBOX_DEBOUNCE)
            .collect { engine.requestSync() }
    }
    DatasetResetDialog(engine)
}

package ch.mathorck.easystock.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import ch.mathorck.easystock.data.db.rememberAppDatabase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Wait after the last local change before a sync, so a burst of edits goes in one push. */
val OUTBOX_DEBOUNCE = 3.seconds

/** No sync on resume within this time after the last good one: switching apps back and forth costs no calls. */
val RESUME_SYNC_INTERVAL = 30.seconds

/**
 * True when coming to the front should sync: never synced yet ([lastSyncAt] null: the full pull after
 * joining), or the last good sync is [RESUME_SYNC_INTERVAL] old (or in the future: the clock moved back).
 * A failed cycle doesn't move [lastSyncAt]: the next resume tries again. Sync now and local changes are not throttled.
 */
fun shouldSyncOnResume(lastSyncAt: Long?, now: Long): Boolean =
    lastSyncAt == null || now < lastSyncAt || now - lastSyncAt >= RESUME_SYNC_INTERVAL.inWholeMilliseconds

/**
 * The sync triggers while connected ([connected]: a `sync_state` row exists): each time the app comes
 * to the front (the first one right after joining runs the full pull; at most once per
 * [RESUME_SYNC_INTERVAL], see [shouldSyncOnResume]), and [OUTBOX_DEBOUNCE] after
 * the last local change. Composes nothing in standalone mode: no engine, no network.
 */
@OptIn(FlowPreview::class)
@Composable
fun SyncHost(connected: Boolean) {
    BackgroundSyncHost(connected)
    if (!connected) return
    val engine = rememberSyncEngine() ?: return
    val db = rememberAppDatabase()
    val dao = remember(db) { db.syncDao() }
    val syncState by remember(db) { db.sessionDao().observeSyncState() }.collectAsState(null)
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(engine, dao) {
        scope.launch {
            if (shouldSyncOnResume(dao.syncState()?.lastSyncAt, Clock.System.now().toEpochMilliseconds())) engine.requestSync()
        }
        onPauseOrDispose {}
    }
    LaunchedEffect(engine, dao) {
        dao.observeOutboxCount()
            .filter { it > 0 }
            .debounce(OUTBOX_DEBOUNCE)
            .collect { engine.requestSync() }
    }
    DatasetResetDialog(engine)
    LocalNetworkPrompt(engine, syncState?.serverUrl)
}

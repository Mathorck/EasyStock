package ch.mathorck.easystock.composables.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ch.mathorck.easystock.enums.sync.BackgroundOutcome
import ch.mathorck.easystock.enums.sync.BackgroundSyncAction
import ch.mathorck.easystock.enums.sync.SyncError
import ch.mathorck.easystock.enums.sync.SyncResult
import ch.mathorck.easystock.services.sync.BackgroundSyncScheduler

/**
 * Connected: the job is scheduled (KEEP, so a job already there stays untouched). Just left the
 * server (logout, leave, revoked session, data reset): the job is cancelled. Standalone phone that
 * was never connected in this run: nothing is done, WorkManager is not even started.
 */
fun backgroundSyncAction(connected: Boolean, wasConnected: Boolean): BackgroundSyncAction = when {
    connected -> BackgroundSyncAction.SCHEDULE
    wasConnected -> BackgroundSyncAction.CANCEL
    else -> BackgroundSyncAction.NONE
}

/** The result of one background cycle ([error]: the engine status after it). */
fun backgroundOutcome(result: SyncResult, error: SyncError?): BackgroundOutcome = when (result) {
    SyncResult.NOT_CONNECTED -> BackgroundOutcome.STOP
    SyncResult.DONE, SyncResult.PAUSED -> BackgroundOutcome.SUCCESS
    SyncResult.FAILED -> when (error) {
        SyncError.NETWORK -> BackgroundOutcome.RETRY
        SyncError.SESSION_REVOKED -> BackgroundOutcome.STOP
        // Refused or unreadable, or the local network not allowed (only the user can fix it, a retry
        // would back off for nothing): the next period tries again
        else -> BackgroundOutcome.SUCCESS
    }
}

/** Null where there is no background job (iOS). */
@Composable
expect fun rememberBackgroundSyncScheduler(): BackgroundSyncScheduler?

/** Keeps the background job in line with [connected] (a `sync_state` row exists). */
@Composable
fun BackgroundSyncHost(connected: Boolean) {
    val scheduler = rememberBackgroundSyncScheduler() ?: return
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(connected, scheduler) {
        when (backgroundSyncAction(connected, wasConnected)) {
            BackgroundSyncAction.SCHEDULE -> scheduler.schedule()
            BackgroundSyncAction.CANCEL -> scheduler.cancel()
            BackgroundSyncAction.NONE -> {}
        }
        wasConnected = connected
    }
}

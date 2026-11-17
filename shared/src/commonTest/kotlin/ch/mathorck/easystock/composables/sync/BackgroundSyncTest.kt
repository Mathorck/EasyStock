package ch.mathorck.easystock.composables.sync

import ch.mathorck.easystock.enums.sync.BackgroundOutcome
import ch.mathorck.easystock.enums.sync.BackgroundSyncAction
import ch.mathorck.easystock.enums.sync.SyncError
import ch.mathorck.easystock.enums.sync.SyncResult
import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundSyncTest {
    @Test
    fun connectedSchedules() {
        assertEquals(BackgroundSyncAction.SCHEDULE, backgroundSyncAction(connected = true, wasConnected = false))
        assertEquals(BackgroundSyncAction.SCHEDULE, backgroundSyncAction(connected = true, wasConnected = true))
    }

    @Test
    fun leavingCancels() {
        assertEquals(BackgroundSyncAction.CANCEL, backgroundSyncAction(connected = false, wasConnected = true))
    }

    @Test
    fun standaloneDoesNothing() {
        assertEquals(BackgroundSyncAction.NONE, backgroundSyncAction(connected = false, wasConnected = false))
    }

    @Test
    fun outcomes() {
        assertEquals(BackgroundOutcome.SUCCESS, backgroundOutcome(SyncResult.DONE, null))
        assertEquals(BackgroundOutcome.SUCCESS, backgroundOutcome(SyncResult.PAUSED, SyncError.APP_TOO_OLD))
        assertEquals(BackgroundOutcome.STOP, backgroundOutcome(SyncResult.NOT_CONNECTED, null))
        assertEquals(BackgroundOutcome.RETRY, backgroundOutcome(SyncResult.FAILED, SyncError.NETWORK))
        assertEquals(BackgroundOutcome.STOP, backgroundOutcome(SyncResult.FAILED, SyncError.SESSION_REVOKED))
        assertEquals(BackgroundOutcome.SUCCESS, backgroundOutcome(SyncResult.FAILED, SyncError.SERVER))
        assertEquals(BackgroundOutcome.SUCCESS, backgroundOutcome(SyncResult.FAILED, SyncError.FORBIDDEN))
        // Only the user can allow the local network: no retry backoff for nothing
        assertEquals(BackgroundOutcome.SUCCESS, backgroundOutcome(SyncResult.FAILED, SyncError.LOCAL_NETWORK_DENIED))
    }
}

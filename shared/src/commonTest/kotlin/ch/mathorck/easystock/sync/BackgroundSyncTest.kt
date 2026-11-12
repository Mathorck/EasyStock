package ch.mathorck.easystock.sync

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
    }
}

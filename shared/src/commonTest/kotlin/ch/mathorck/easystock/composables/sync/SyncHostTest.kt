package ch.mathorck.easystock.composables.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncHostTest {
    @Test
    fun aResumeSyncsAtMostOnceEveryThirtySeconds() {
        val last = 1_000_000L
        assertTrue(shouldSyncOnResume(lastSyncAt = null, now = last))
        assertFalse(shouldSyncOnResume(lastSyncAt = last, now = last))
        assertFalse(shouldSyncOnResume(lastSyncAt = last, now = last + 29_999))
        assertTrue(shouldSyncOnResume(lastSyncAt = last, now = last + 30_000))
        // The clock moved back: no waiting on a time in the future
        assertTrue(shouldSyncOnResume(lastSyncAt = last, now = last - 1))
    }
}

package ch.mathorck.easystock.reset

import ch.mathorck.easystock.auth.ServerSessionEvents
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevokeBackupTest {
    @AfterTest
    fun tearDown() {
        ServerSessionEvents.leaving = false
        ServerSessionEvents.revokedHandled()
    }

    @Test
    fun noBackupWithoutUnsentChanges() = runTest {
        var backups = 0
        val result = backupUnsentChanges({ 0 }) { backups++; "auto-1" }
        assertEquals(RevokeBackup(0), result)
        assertEquals(0, backups)
    }

    @Test
    fun unsentChangesAreBackedUp() = runTest {
        val result = backupUnsentChanges({ 3 }) { "auto-20261114-1200.easystock" }
        assertEquals(RevokeBackup(3, backupFile = "auto-20261114-1200.easystock"), result)
    }

    @Test
    fun aFailedBackupIsReported() = runTest {
        val result = backupUnsentChanges({ 2 }) { error("disk full") }
        assertEquals(RevokeBackup(2, backupFailed = true), result)
    }

    @Test
    fun changesThatCantBeCountedAreBackedUpAnyway() = runTest {
        val result = backupUnsentChanges({ error("database closed") }) { "auto-1" }
        assertEquals("auto-1", result.backupFile)
    }

    @Test
    fun aLocalLossIsToldApartFromAServerRevoke() {
        ServerSessionEvents.sessionRevoked(lostLocally = true)
        assertTrue(ServerSessionEvents.revoked.value)
        assertTrue(ServerSessionEvents.lostLocally)
        ServerSessionEvents.revokedHandled()
        assertFalse(ServerSessionEvents.lostLocally)

        ServerSessionEvents.sessionRevoked(lostLocally = false)
        // The token is cleared after the revoke: a later read finds it missing, the cause stays the server
        ServerSessionEvents.sessionRevoked(lostLocally = true)
        assertFalse(ServerSessionEvents.lostLocally)
    }

    @Test
    fun aRevokeWhileLeavingIsIgnored() {
        ServerSessionEvents.leaving = true
        ServerSessionEvents.sessionRevoked()
        assertFalse(ServerSessionEvents.revoked.value)
    }
}

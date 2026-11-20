package ch.mathorck.easystock.services.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Guard: these are the names an install made before profiles already holds. Never change a value. */
class ProfilePathsTest {
    @Test
    fun legacyNamesAreTheOnesUsedBeforeProfiles() {
        val legacy = ProfilePaths.LEGACY
        assertEquals("easystock.db", legacy.databaseName)
        assertEquals("session.preferences_pb", legacy.sessionFile)
        assertEquals("easystock_prefs", legacy.appPrefsName)
        assertEquals("local_network", legacy.localNetworkPrefsName)
        assertEquals("sync_reset_notice", legacy.resetNoticePrefsName)
        assertEquals("sync_dataset", legacy.datasetEpochPrefsName)
        assertEquals("unifiedpush.json", legacy.pushFile)
        assertEquals("auto-backups", legacy.autoBackupDir)
        assertEquals("easystock-background-sync", legacy.backgroundSyncWork)
        assertEquals("easystock-push-sync", legacy.pushSyncWork)
        assertEquals("easystock-date-alerts", legacy.dateAlertWork)
        assertEquals("", legacy.filesRoot)
        assertEquals("default", legacy.pushInstance)
    }

    private fun names(paths: ProfilePaths) = listOf(
        paths.databaseName, paths.sessionFile, paths.appPrefsName, paths.localNetworkPrefsName,
        paths.resetNoticePrefsName, paths.datasetEpochPrefsName, paths.pushFile, paths.autoBackupDir,
        paths.backgroundSyncWork, paths.pushSyncWork, paths.dateAlertWork, paths.filesRoot, paths.pushInstance,
    )

    @Test
    fun aProfileSharesNoNameWithTheLegacyOneOrAnother() {
        val a = names(ProfilePaths.forProfile(ID_A))
        val b = names(ProfilePaths.forProfile(ID_B))
        val legacy = names(ProfilePaths.LEGACY)
        a.indices.forEach { i ->
            assertTrue(a[i] != legacy[i] && a[i] != b[i], "name $i")
        }
    }

    @Test
    fun aProfileKeepsItsFilesInItsOwnFolder() {
        val paths = ProfilePaths.forProfile(ID_A)
        assertEquals("profiles/$ID_A", paths.filesRoot)
        // Not in the legacy auto-backups folder: a wipe of the legacy profile deletes that one
        assertEquals("profiles/$ID_A/auto-backups", paths.autoBackupDir)
        assertEquals("easystock-$ID_A.db", paths.databaseName)
    }

    @Test
    fun onlyUuidsAreAccepted() {
        listOf("legacy", "../x", "", ID_A.uppercase(), "$ID_A/..").forEach {
            assertFailsWith<IllegalArgumentException>(it) { ProfilePaths.forProfile(it) }
        }
    }

    companion object {
        const val ID_A = "0b6f3c3e-2d1a-4c8e-9f5b-7a2e4d6c8b10"
        const val ID_B = "9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4"
    }
}

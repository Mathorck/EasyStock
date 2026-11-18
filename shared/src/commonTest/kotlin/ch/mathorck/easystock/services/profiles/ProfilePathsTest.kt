package ch.mathorck.easystock.services.profiles

import kotlin.test.Test
import kotlin.test.assertEquals

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
    }
}

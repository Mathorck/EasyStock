package ch.mathorck.easystock.services.profiles

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.services.sync.AndroidDatasetEpochStore
import ch.mathorck.easystock.services.sync.AndroidResetNoticeStore
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * An install made before profiles: data written under the literal old names is read through the
 * profile plumbing. The original values are put back, the app data of the device is not lost.
 */
class LegacyProfileUpgradeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun databaseAndSessionFileKeepTheirOldLocations() {
        assertEquals(context.getDatabasePath("easystock.db"), context.getDatabasePath(activeProfilePaths.databaseName))
        assertEquals("session.preferences_pb", activeProfilePaths.sessionFile)
    }

    @Test
    fun datasetEpochIsReadFromTheOldPreferencesFile() = runBlocking {
        val old = context.getSharedPreferences("sync_dataset", Context.MODE_PRIVATE)
        val before = old.getString("dataset_epoch", null)
        try {
            old.edit().putString("dataset_epoch", "legacy-epoch").commit()
            assertEquals("legacy-epoch", AndroidDatasetEpochStore(context).load())
        } finally {
            old.edit().apply { if (before == null) remove("dataset_epoch") else putString("dataset_epoch", before) }.commit()
        }
    }

    @Test
    fun resetNoticeIsReadFromTheOldPreferencesFile() = runBlocking {
        val old = context.getSharedPreferences("sync_reset_notice", Context.MODE_PRIVATE)
        val before = old.getString("notice", null)
        try {
            old.edit().putString("notice", "legacy-notice").commit()
            assertEquals("legacy-notice", AndroidResetNoticeStore(context).load())
        } finally {
            old.edit().apply { if (before == null) remove("notice") else putString("notice", before) }.commit()
        }
    }
}

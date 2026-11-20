package ch.mathorck.easystock.services.profiles

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import ch.mathorck.easystock.services.reset.wipeAppFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush

internal class AndroidProfilePlatform(context: Context) : ProfilePlatform {
    private val app = context.applicationContext

    override suspend fun wipe(paths: ProfilePaths) = withContext(Dispatchers.IO) {
        cancelWork(paths)
        val pushFile = File(app.noBackupFilesDir, paths.pushFile)
        // The distributor forgets the push address of the profile (no network: the server is not told).
        // Only when it registered one: the call creates the connector's own store otherwise
        if (pushFile.exists()) UnifiedPush.unregister(app, paths.pushInstance)
        app.deleteDatabase(paths.databaseName)
        // Room's lock file, which deleteDatabase leaves
        app.getDatabasePath("${paths.databaseName}.lck").delete()
        File(app.filesDir, "datastore/${paths.sessionFile}").delete()
        listOf(paths.appPrefsName, paths.localNetworkPrefsName, paths.resetNoticePrefsName, paths.datasetEpochPrefsName)
            .forEach { app.deleteSharedPreferences(it) }
        pushFile.delete()
        if (paths.filesRoot.isEmpty()) {
            // The legacy root is filesDir itself, which holds the other profiles: only its own folders
            // The cache is shared and holds the files of the profile in use: kept
            wipeAppFiles(app.filesDir, cacheDir = null, autoBackupDir = File(app.filesDir, paths.autoBackupDir))
        } else {
            File(app.filesDir, paths.filesRoot).deleteRecursively()
        }
        Unit
    }

    override fun stopBackgroundWork(paths: ProfilePaths) {
        cancelWork(paths)
        // A tapped alert of the left profile would open an article of another database
        NotificationManagerCompat.from(app).cancelAll()
    }

    private fun cancelWork(paths: ProfilePaths) {
        val work = WorkManager.getInstance(app)
        listOf(paths.backgroundSyncWork, paths.pushSyncWork, paths.dateAlertWork).forEach { work.cancelUniqueWork(it) }
    }

    override fun restart() {
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        app.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}

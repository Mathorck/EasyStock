package ch.mathorck.easystock.services.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.dao.alerts.asAlertStore
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.services.alerts.AlertNotifier
import ch.mathorck.easystock.services.alerts.alertNotifications
import ch.mathorck.easystock.services.backup.backupService
import ch.mathorck.easystock.services.server.KtorAccountApi
import ch.mathorck.easystock.services.server.ServerConnection
import ch.mathorck.easystock.services.server.isLocalNetworkAccessMissing
import ch.mathorck.easystock.services.server.serverClientFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** Preferences file of [AndroidDatasetEpochStore]. */
const val DATASET_EPOCH_FILE = "sync_dataset"

@Volatile
private var engine: SyncEngine? = null

/** The engine of the process: one cycle at a time for every screen. */
fun syncEngine(context: Context): SyncEngine = engine ?: synchronized(SyncEngine::class) {
    engine ?: run {
        val app = context.applicationContext
        SyncEngine(
            store = RoomSyncStore(appDatabase(app), AndroidSyncImageFiles(app.filesDir)),
            // The shared client of the connection (token refreshes never race), built on the main thread
            remote = { state ->
                val client = withContext(Dispatchers.Main) { serverClientFor(app, ServerConnection.of(state)) }
                SyncRemote(KtorSyncApi(client), KtorAccountApi(client))
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            onImagesDownloaded = SyncImages::downloaded,
            afterPull = AlertNotifier(appDatabase(app).alertDao().asAlertStore(), alertNotifications(app))::check,
            onDatasetReset = alertNotifications(app)::cancelAll,
            epochs = AndroidDatasetEpochStore(app),
            backupBeforeReset = AndroidAutoBackups(autoBackupDir(app), backupService(app))::create,
            resetNotices = AndroidResetNoticeStore(app),
            localNetworkMissing = { isLocalNetworkAccessMissing(app) },
            refusedImageStore = AndroidRefusedImageStore(app),
        ).also { engine = it }
    }
}

@Composable
actual fun rememberSyncEngine(): SyncEngine? = syncEngine(LocalContext.current)

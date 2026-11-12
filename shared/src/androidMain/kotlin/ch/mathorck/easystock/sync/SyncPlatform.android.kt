package ch.mathorck.easystock.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.alerts.AlertNotifier
import ch.mathorck.easystock.alerts.alertNotifications
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.data.db.asAlertStore
import ch.mathorck.easystock.images.isValidImagePath
import ch.mathorck.easystock.server.KtorAccountApi
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.serverClientFor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** The photo files in `filesDir/images`, as the article image store reads them. */
internal class AndroidSyncImageFiles(private val filesDir: File) : SyncImageFiles {
    private fun fileOf(path: String): File? = if (isValidImagePath(path)) File(filesDir, path) else null

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        fileOf(path)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { fileOf(path)?.isFile == true }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = fileOf(path) ?: return@withContext
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.download")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) error("Could not store the photo")
        } finally {
            temp.delete()
        }
    }
}

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
        ).also { engine = it }
    }
}

@Composable
actual fun rememberSyncEngine(): SyncEngine? = syncEngine(LocalContext.current)

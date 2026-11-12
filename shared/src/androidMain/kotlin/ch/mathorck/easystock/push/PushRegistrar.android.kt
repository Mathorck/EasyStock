package ch.mathorck.easystock.push

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.serverClientFor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.unifiedpush.android.connector.UnifiedPush

/** [PushRecord] as JSON in `noBackupFilesDir`: an Android backup never brings an old address back. */
internal class FilePushStore(private val file: File) : PushStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val _record = MutableStateFlow(load())
    override val record: StateFlow<PushRecord> = _record.asStateFlow()

    private fun load(): PushRecord = runCatching {
        if (file.isFile) json.decodeFromString(PushRecord.serializer(), file.readText()) else PushRecord()
    }.getOrDefault(PushRecord())

    override suspend fun save(record: PushRecord) {
        _record.value = record
        withContext(Dispatchers.IO) {
            if (record == PushRecord()) {
                file.delete()
            } else {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(PushRecord.serializer(), record))
                if (!temp.renameTo(file)) Log.w(TAG, "Could not save the push state")
            }
        }
    }
}

/** The UnifiedPush connector library. No VAPID key: the server sends a plain "sync" body. */
internal class AndroidPushConnector(private val context: Context) : PushConnector {
    override fun distributors(): List<PushDistributor> = UnifiedPush.getDistributors(context)
        .filter { it != context.packageName }
        .map { PushDistributor(it, labelOf(it)) }

    private fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    override fun use(distributor: String) {
        UnifiedPush.saveDistributor(context, distributor)
        UnifiedPush.register(context)
    }

    override fun register() {
        // Null when the distributor was uninstalled: the library then calls onUnregistered
        if (UnifiedPush.getSavedDistributor(context) != null) UnifiedPush.register(context)
    }

    override fun unregister() {
        UnifiedPush.unregister(context)
    }
}

@Volatile
private var registrar: PushRegistrar? = null

/** The registrar of the process, shared by the Account screen and [EasyStockPushService]. */
fun pushRegistrar(context: Context): PushRegistrar = registrar ?: synchronized(PushRegistrar::class) {
    registrar ?: run {
        val app = context.applicationContext
        PushRegistrar(
            store = FilePushStore(File(app.noBackupFilesDir, PUSH_FILE)),
            connector = AndroidPushConnector(app),
            target = {
                appDatabase(app).sessionDao().syncState()?.let { state ->
                    val connection = ServerConnection.of(state)
                    // The shared client of the connection (token refreshes never race), built on the main thread
                    val client = withContext(Dispatchers.Main) { serverClientFor(app, connection) }
                    PushTarget(connection.serverUrl, KtorPushApi(client))
                }
            },
        ).also { registrar = it }
    }
}

@Composable
actual fun rememberPushRegistrar(): PushRegistrar? = pushRegistrar(LocalContext.current)

private const val PUSH_FILE = "unifiedpush.json"
private const val TAG = "EasyStockPush"

package ch.mathorck.easystock.services.push

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.services.server.ServerConnection
import ch.mathorck.easystock.services.server.serverClientFor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

internal const val TAG = "EasyStockPush"

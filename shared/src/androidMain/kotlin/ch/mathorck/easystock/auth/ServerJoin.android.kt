package ch.mathorck.easystock.auth

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.images.IMAGE_DIR
import ch.mathorck.easystock.server.ClientVersions
import ch.mathorck.easystock.server.KtorJoinApi
import ch.mathorck.easystock.server.appVersionName
import ch.mathorck.easystock.server.createPublicClient
import ch.mathorck.easystock.server.serverHttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** True when the photo folder of [filesDir] holds a file (in any sub-folder). */
suspend fun hasPhotoFiles(filesDir: File): Boolean = withContext(Dispatchers.IO) {
    File(filesDir, IMAGE_DIR).walk().any { it.isFile }
}

/** Shown to the Admin in the device list: "Google Pixel 10 Pro". */
internal fun deviceName(): String {
    val model = Build.MODEL.orEmpty()
    val maker = Build.MANUFACTURER.orEmpty()
    val name = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
    return name.trim().ifEmpty { "Android" }.take(100)
}

fun joinPlatform(context: Context): JoinPlatform {
    val app = context.applicationContext
    val dao = appDatabase(app).sessionDao()
    val versions = ClientVersions(appVersion = appVersionName(app))
    return JoinPlatform(
        emptyCheck = EmptyPhoneCheck(dao) { hasPhotoFiles(app.filesDir) },
        join = ServerJoin(dao, sessionStore(app)),
        joinApi = { connection ->
            KtorJoinApi(createPublicClient(connection, serverHttpEngine(connection.fingerprint), versions))
        },
        deviceName = deviceName(),
        appVersion = versions.appVersion,
    )
}

@Composable
actual fun rememberJoinPlatform(): JoinPlatform {
    val context = LocalContext.current.applicationContext
    return remember(context) { joinPlatform(context) }
}

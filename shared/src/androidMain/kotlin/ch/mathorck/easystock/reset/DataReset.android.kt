package ch.mathorck.easystock.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.auth.sessionStore
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.images.IMAGE_DIR
import ch.mathorck.easystock.server.releaseServerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes the files the app owns, except the database (emptied by [DataReset]) and the platform
 * settings (app language):
 * - `filesDir/images`: article photos, and the `images.incoming` / `images.old` folders a restore
 *   may have left behind;
 * - `cacheDir/camera`: a photo waiting for the crop screen;
 * - `cacheDir/backup`: exported or received backups, which hold the data being erased.
 */
suspend fun wipeAppFiles(filesDir: File, cacheDir: File) = withContext(Dispatchers.IO) {
    listOf(
        File(filesDir, IMAGE_DIR),
        File(filesDir, "$IMAGE_DIR.incoming"),
        File(filesDir, "$IMAGE_DIR.old"),
        File(cacheDir, "camera"),
        File(cacheDir, "backup"),
    ).forEach { it.deleteRecursively() }
}

@Composable
actual fun rememberDataReset(): DataReset {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        DataReset(
            db = appDatabase(context),
            wipeFiles = { wipeAppFiles(context.filesDir, context.cacheDir) },
            clearSession = {
                withContext(Dispatchers.Main) { releaseServerClient() }
                sessionStore(context).clear()
            },
        )
    }
}

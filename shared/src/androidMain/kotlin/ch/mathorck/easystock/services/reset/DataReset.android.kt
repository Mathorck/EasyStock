package ch.mathorck.easystock.services.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.services.auth.sessionStore
import ch.mathorck.easystock.services.backup.PHOTO_STAGING_DIR
import ch.mathorck.easystock.services.backup.backupService
import ch.mathorck.easystock.services.images.IMAGE_DIR
import ch.mathorck.easystock.services.push.pushRegistrar
import ch.mathorck.easystock.services.server.releaseServerClient
import ch.mathorck.easystock.services.sync.AndroidAutoBackups
import ch.mathorck.easystock.services.sync.autoBackupDir
import ch.mathorck.easystock.services.sync.syncEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes the files the app owns, except the database (emptied by [DataReset]) and the platform
 * settings (app language) and the
 * `easystock_prefs` preferences (phone colors):
 * - `filesDir/images`: article photos, and the `images.incoming` / `images.old` folders a restore
 *   may have left behind, and `images.extracting` (photos of a backup being extracted);
 * - `cacheDir/camera`: a photo waiting for the crop screen;
 * - `cacheDir/backup`: exported or received backups, which hold the data being erased;
 * - `cacheDir/server`: files of the admin screens (server backup, invite PDF with a live token);
 * - `filesDir/auto-backups`: the automatic backups written before a server reset, unless
 *   [keepAutoBackups] (the wipe after a revoked session keeps the backup of the unsent changes).
 */
suspend fun wipeAppFiles(filesDir: File, cacheDir: File, keepAutoBackups: Boolean = false) = withContext(Dispatchers.IO) {
    listOfNotNull(
        File(filesDir, IMAGE_DIR),
        File(filesDir, "$IMAGE_DIR.incoming"),
        File(filesDir, "$IMAGE_DIR.old"),
        File(filesDir, PHOTO_STAGING_DIR),
        File(cacheDir, "camera"),
        File(cacheDir, "backup"),
        // Admin screens: a downloaded server backup, a picked import, the Guest invite PDF (live token)
        File(cacheDir, "server"),
        // Automatic backups written before a server reset: they hold the data being erased
        File(filesDir, "auto-backups").takeUnless { keepAutoBackups },
    ).forEach { it.deleteRecursively() }
}

@Composable
actual fun rememberDataReset(): DataReset {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        DataReset(
            db = appDatabase(context),
            wipeFiles = { keepAutoBackups -> wipeAppFiles(context.filesDir, context.cacheDir, keepAutoBackups) },
            pendingChanges = { appDatabase(context).sessionDao().outboxCount() },
            autoBackup = AndroidAutoBackups(autoBackupDir(context), backupService(context))::create,
            clearSession = {
                // The distributor forgets this app (no network: the session may be revoked already)
                pushRegistrar(context).forget()
                withContext(Dispatchers.Main) { releaseServerClient() }
                sessionStore(context).clear()
            },
            forgetSync = { syncEngine(context).forgetServer() },
        )
    }
}

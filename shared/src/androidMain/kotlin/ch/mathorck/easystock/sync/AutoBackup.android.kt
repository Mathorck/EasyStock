package ch.mathorck.easystock.sync

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import ch.mathorck.easystock.backup.BackupService
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.backup_share_title
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Folder of the automatic backups (`filesDir/auto-backups`): kept, unlike the temporary backup folder in the cache. */
internal fun autoBackupDir(context: Context): File = File(context.applicationContext.filesDir, "auto-backups")

/**
 * Writes the automatic backup before a reset wipe: a normal backup (same format as a manual one, no
 * password) moved to [dir]. Only the newest [AutoBackups.KEEP] stay.
 */
class AndroidAutoBackups(private val dir: File, private val service: BackupService) {
    /** Returns the name of the new file. Throws when the backup fails. */
    suspend fun create(): String {
        val export = service.export(null)
        try {
            return withContext(Dispatchers.IO) {
                dir.mkdirs()
                val target = File(dir, AutoBackups.PREFIX + export.fileName)
                // Written under a name no listing sees, then renamed: never a half file with a backup name
                val part = File(dir, "${target.name}.part")
                File(export.path).copyTo(part, overwrite = true)
                if (!part.renameTo(target)) {
                    part.delete()
                    error("Could not store the backup")
                }
                prune()
                target.name
            }
        } finally {
            service.discard(export)
        }
    }

    fun prune() {
        val names = dir.list()?.toList().orEmpty()
        AutoBackups.toDelete(names).forEach { File(dir, it).delete() }
    }
}

/** [ResetNoticeStore] in a small private preferences file (counts and a file name, no personal data). */
internal class AndroidResetNoticeStore(context: Context) : ResetNoticeStore {
    private val prefs = context.getSharedPreferences(RESET_NOTICE_FILE, Context.MODE_PRIVATE)

    override suspend fun load(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY, null) }

    override suspend fun save(text: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply { if (text == null) remove(KEY) else putString(KEY, text) }.commit()
        Unit
    }

    private companion object {
        const val KEY = "notice"
    }
}

/** Preferences file of [AndroidResetNoticeStore]. */
const val RESET_NOTICE_FILE = "sync_reset_notice"

@Composable
actual fun rememberAutoBackupSharer(): (String) -> Unit {
    val context = LocalContext.current
    val title = stringResource(Res.string.backup_share_title)
    return remember(context, title) {
        { name: String ->
            // Only a plain file name of the folder: the argument comes from a stored notice
            val file = File(autoBackupDir(context), name)
            if (name == file.name && name.startsWith(AutoBackups.PREFIX) && file.isFile) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, title)
                try {
                    context.startActivity(chooser)
                } catch (e: ActivityNotFoundException) {
                }
            }
        }
    }
}

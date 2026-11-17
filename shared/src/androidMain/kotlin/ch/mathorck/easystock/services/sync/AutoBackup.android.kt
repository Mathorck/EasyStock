package ch.mathorck.easystock.services.sync

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.backup_share_title
import java.io.File
import org.jetbrains.compose.resources.stringResource

/** Folder of the automatic backups (`filesDir/auto-backups`): kept, unlike the temporary backup folder in the cache. */
internal fun autoBackupDir(context: Context): File = File(context.applicationContext.filesDir, "auto-backups")

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

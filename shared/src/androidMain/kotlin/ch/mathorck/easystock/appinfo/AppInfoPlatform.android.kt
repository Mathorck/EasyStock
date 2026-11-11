package ch.mathorck.easystock.appinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.nfc.NfcAdapter
import android.os.Build
import android.os.StatFs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.db.DATABASE_NAME
import ch.mathorck.easystock.nfc.NfcAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class AndroidAppInfoPlatform(context: Context) : AppInfoPlatform {
    private val context = context.applicationContext

    override suspend fun read(): PlatformInfo = withContext(Dispatchers.IO) {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        // The WAL and shared-memory files belong to the database
        val dbBytes = listOf("", "-wal", "-shm").sumOf { File(dbFile.path + it).takeIf(File::isFile)?.length() ?: 0L }
        val photos = File(context.filesDir, "images").walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList()
        val nfc = when (val adapter = NfcAdapter.getDefaultAdapter(context)) {
            null -> NfcAvailability.Unavailable
            else -> if (adapter.isEnabled) NfcAvailability.Enabled else NfcAvailability.Disabled
        }
        PlatformInfo(
            versionName = pkg.versionName ?: "?",
            versionCode = pkg.longVersionCode,
            debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            applicationId = context.packageName,
            installedAt = pkg.firstInstallTime.takeIf { it > 0 },
            updatedAt = pkg.lastUpdateTime.takeIf { it > 0 },
            os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            locale = context.resources.configuration.locales[0].toLanguageTag(),
            nfc = nfc,
            databaseBytes = dbBytes,
            photoBytes = photos.sumOf { it.length() },
            photoCount = photos.size,
            freeBytes = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrNull(),
        )
    }

    override fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyStock app info", text))
    }
}

@Composable
actual fun rememberAppInfoPlatform(): AppInfoPlatform {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppInfoPlatform(context) }
}

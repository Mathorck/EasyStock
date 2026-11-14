package ch.mathorck.easystock.server

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** `Manifest.permission.ACCESS_LOCAL_NETWORK` (Android 17, API 37). */
private const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private const val API_LOCAL_NETWORK = 37

private const val PREFS_FILE = "local_network"

/** Set after a denial: from then on, no rationale means the system won't show its dialog any more. */
private const val KEY_DENIED = "denied_once"

/** True when the system blocks the local network for this app (Android 17+, not granted). Any context. */
fun isLocalNetworkAccessMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= API_LOCAL_NETWORK &&
        ContextCompat.checkSelfPermission(context.applicationContext, PERMISSION) != PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun rememberLocalNetworkAccess(onResult: (granted: Boolean) -> Unit): LocalNetworkAccess {
    val context = LocalContext.current
    val app = context.applicationContext
    val prefs = remember(app) { app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE) }
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) prefs.edit().putBoolean(KEY_DENIED, true).apply()
        currentOnResult(granted)
    }
    return remember(context, launcher) {
        object : LocalNetworkAccess {
            override fun isMissing() = isLocalNetworkAccessMissing(app)

            override fun mustOpenSettings(): Boolean {
                if (!isMissing()) return false
                val activity = context.findActivity() ?: return false
                // Before the first ask there is no rationale either: only a denial seen before counts
                return prefs.getBoolean(KEY_DENIED, false) && !activity.shouldShowRequestPermissionRationale(PERMISSION)
            }

            override fun request() {
                if (isMissing()) launcher.launch(PERMISSION) else currentOnResult(true)
            }

            override fun openSettings() {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

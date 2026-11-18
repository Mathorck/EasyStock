package ch.mathorck.easystock.services.server

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
import ch.mathorck.easystock.services.profiles.activeProfilePaths

/** `Manifest.permission.ACCESS_LOCAL_NETWORK` (Android 17, API 37). */
private const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private const val API_LOCAL_NETWORK = 37


/** Denied for good ([deniedForGood]): the system won't show its dialog any more, only the settings can allow it. */
private const val KEY_DENIED = "denied_for_good"

/** The rationale just before the last dialog, kept across an activity recreation while it shows. */
private const val KEY_RATIONALE_BEFORE = "rationale_before"

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
    val prefs = remember(app) { app.getSharedPreferences(activeProfilePaths.localNetworkPrefsName, Context.MODE_PRIVATE) }
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rationaleNow = context.findActivity()?.shouldShowRequestPermissionRationale(PERMISSION) ?: false
        val forGood = deniedForGood(
            previous = prefs.getBoolean(KEY_DENIED, false),
            granted = granted,
            rationaleBefore = prefs.getBoolean(KEY_RATIONALE_BEFORE, false),
            rationaleNow = rationaleNow,
        )
        // A grant resets the denial: a revoke after it may ask again
        prefs.edit().putBoolean(KEY_DENIED, forGood).remove(KEY_RATIONALE_BEFORE).apply()
        currentOnResult(granted)
    }
    return remember(context, launcher) {
        object : LocalNetworkAccess {
            override fun isMissing(): Boolean {
                val missing = isLocalNetworkAccessMissing(app)
                // Allowed meanwhile (e.g. in the settings): a later revoke may ask again
                if (!missing && prefs.getBoolean(KEY_DENIED, false)) prefs.edit().remove(KEY_DENIED).apply()
                return missing
            }

            override fun mustOpenSettings(): Boolean {
                if (!isMissing()) return false
                val activity = context.findActivity() ?: return false
                // A rationale again means the user reset the permission meanwhile: the dialog can show
                return prefs.getBoolean(KEY_DENIED, false) && !activity.shouldShowRequestPermissionRationale(PERMISSION)
            }

            override fun request() {
                if (!isMissing()) return currentOnResult(true)
                val rationale = context.findActivity()?.shouldShowRequestPermissionRationale(PERMISSION) ?: false
                prefs.edit().putBoolean(KEY_RATIONALE_BEFORE, rationale).apply()
                launcher.launch(PERMISSION)
            }

            override fun openSettings() {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

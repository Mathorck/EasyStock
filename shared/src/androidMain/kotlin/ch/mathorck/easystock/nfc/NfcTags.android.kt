package ch.mathorck.easystock.nfc

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Every tag technology the phone can poll for. NDEF is not read: only the UID is used. */
private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
    NfcAdapter.FLAG_READER_NFC_B or
    NfcAdapter.FLAG_READER_NFC_F or
    NfcAdapter.FLAG_READER_NFC_V or
    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun availabilityOf(adapter: NfcAdapter?): NfcAvailability = when {
    adapter == null -> NfcAvailability.Unavailable
    adapter.isEnabled -> NfcAvailability.Enabled
    else -> NfcAvailability.Disabled
}

/** Short buzz when a tag is read. Silently skipped when the phone can't vibrate. */
private fun vibrate(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: RuntimeException) {
        // Vibration is only a hint
    }
}

@Composable
actual fun NfcReaderHost(tags: NfcTags) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val listening by tags.listening.collectAsState()
    val availability by tags.availability.collectAsState()
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    // Availability: checked now, on every resume (back from the settings), and when NFC is toggled
    DisposableEffect(lifecycleOwner, adapter) {
        tags.setAvailability(availabilityOf(adapter))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    tags.setAvailability(availabilityOf(adapter))
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val receiver = if (adapter != null) {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    tags.setAvailability(availabilityOf(adapter))
                }
            }.also {
                ContextCompat.registerReceiver(
                    context,
                    it,
                    IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        } else {
            null
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
        }
    }

    // Reader mode only while someone listens and the activity is resumed (the system requires it)
    val active = listening && resumed && availability == NfcAvailability.Enabled
    DisposableEffect(active, activity, adapter) {
        if (!active || activity == null || adapter == null) return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        val appContext = context.applicationContext
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            // Binder thread: read the UID here, hand it to the UI on the main thread
            val event = tagIdOf(tag.id ?: ByteArray(0))?.let { TagEvent.Read(it) } ?: TagEvent.Failed
            main.post { if (tags.dispatch(event) && event is TagEvent.Read) vibrate(appContext) }
        }
        try {
            adapter.enableReaderMode(activity, callback, READER_FLAGS, null)
        } catch (e: RuntimeException) {
            // Not resumed anymore, or NFC turned off meanwhile: the next resume tries again
        }
        onDispose {
            main.removeCallbacksAndMessages(null)
            try {
                adapter.disableReaderMode(activity)
            } catch (e: RuntimeException) {
                // Already disabled by the system (activity paused or destroyed)
            }
        }
    }
}

@Composable
actual fun rememberOpenNfcSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val opened = listOf(Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS).any { action ->
                try {
                    context.startActivity(Intent(action).apply {
                        if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
            if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

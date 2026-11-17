package ch.mathorck.easystock.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.mathorck.easystock.composables.sync.BACKGROUND_SYNC_WORK
import ch.mathorck.easystock.composables.sync.PUSH_CHANNEL_ID
import ch.mathorck.easystock.composables.sync.PUSH_NOTIFICATION_ID
import ch.mathorck.easystock.composables.sync.PUSH_SYNC_WORK
import ch.mathorck.easystock.composables.sync.backgroundOutcome
import ch.mathorck.easystock.enums.sync.BackgroundOutcome
import ch.mathorck.easystock.services.sync.syncEngine
import ch.mathorck.easystock.shared.R
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.push_sync_channel_name
import easystock.shared.generated.resources.push_sync_notification_title
import org.jetbrains.compose.resources.getString

/**
 * One sync cycle through the engine of the process (the same one as the screens use, so a cycle
 * already running is waited for, never doubled). Retries with backoff when the network failed;
 * cancels itself when the phone is no longer connected.
 */
class BackgroundSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = syncEngine(applicationContext)
        val result = engine.sync()
        return when (backgroundOutcome(result, engine.status.value.error)) {
            BackgroundOutcome.SUCCESS -> Result.success()
            BackgroundOutcome.RETRY -> Result.retry()
            BackgroundOutcome.STOP -> {
                val manager = WorkManager.getInstance(applicationContext)
                manager.cancelUniqueWork(BACKGROUND_SYNC_WORK)
                manager.cancelUniqueWork(PUSH_SYNC_WORK)
                Result.success()
            }
        }
    }

    /** Expedited jobs on Android 11 and older run as a foreground service: a quiet notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(PUSH_CHANNEL_ID, getString(Res.string.push_sync_channel_name), NotificationManager.IMPORTANCE_MIN)
            manager?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, PUSH_CHANNEL_ID)
            .setContentTitle(getString(Res.string.push_sync_notification_title))
            .setSmallIcon(R.drawable.ic_stat_low_stock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PUSH_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PUSH_NOTIFICATION_ID, notification)
        }
    }
}

package ch.mathorck.easystock.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import ch.mathorck.easystock.shared.R
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.push_sync_channel_name
import easystock.shared.generated.resources.push_sync_notification_title
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal const val BACKGROUND_SYNC_WORK = "easystock-background-sync"

/** The request of the job: every 15 minutes (the WorkManager minimum), only with a network. */
internal fun backgroundSyncRequest() = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
    .build()

/** Unique name of the one-off sync a push message starts. */
internal const val PUSH_SYNC_WORK = "easystock-push-sync"
private const val PUSH_CHANNEL_ID = "push_sync"
private const val PUSH_NOTIFICATION_ID = 4201

/** The request of a push-started sync: once, expedited (a normal job when the quota is spent), only with a network. */
internal fun pushSyncRequest() = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    .build()

/**
 * Starts a sync that survives the process being stopped. KEEP: pushes of a burst give one job
 * (a push arriving while it runs is covered, the cycle pulls everything the server has).
 */
fun enqueuePushSync(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(PUSH_SYNC_WORK, ExistingWorkPolicy.KEEP, pushSyncRequest())
}

internal class WorkManagerBackgroundSync(private val context: Context) : BackgroundSyncScheduler {
    override fun schedule() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKGROUND_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            backgroundSyncRequest(),
        )
    }

    override fun cancel() {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(BACKGROUND_SYNC_WORK)
        manager.cancelUniqueWork(PUSH_SYNC_WORK)
    }
}

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

@Composable
actual fun rememberBackgroundSyncScheduler(): BackgroundSyncScheduler? {
    val context = LocalContext.current.applicationContext
    return remember(context) { WorkManagerBackgroundSync(context) }
}

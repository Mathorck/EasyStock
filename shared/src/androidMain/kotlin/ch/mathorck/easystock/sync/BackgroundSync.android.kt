package ch.mathorck.easystock.sync

import android.content.Context
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

internal class WorkManagerBackgroundSync(private val context: Context) : BackgroundSyncScheduler {
    override fun schedule() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKGROUND_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            backgroundSyncRequest(),
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BACKGROUND_SYNC_WORK)
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
                WorkManager.getInstance(applicationContext).cancelUniqueWork(BACKGROUND_SYNC_WORK)
                Result.success()
            }
        }
    }
}

@Composable
actual fun rememberBackgroundSyncScheduler(): BackgroundSyncScheduler? {
    val context = LocalContext.current.applicationContext
    return remember(context) { WorkManagerBackgroundSync(context) }
}

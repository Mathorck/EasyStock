package ch.mathorck.easystock.composables.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ch.mathorck.easystock.services.profiles.activeProfilePaths
import ch.mathorck.easystock.services.sync.BackgroundSyncScheduler
import ch.mathorck.easystock.services.sync.WorkManagerBackgroundSync
import ch.mathorck.easystock.sync.BackgroundSyncWorker
import java.util.concurrent.TimeUnit

internal val BACKGROUND_SYNC_WORK get() = activeProfilePaths.backgroundSyncWork

/** The request of the job: every 15 minutes (the WorkManager minimum), only with a network. */
internal fun backgroundSyncRequest() = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
    .build()

/** Unique name of the one-off sync a push message starts. */
internal val PUSH_SYNC_WORK get() = activeProfilePaths.pushSyncWork

internal const val PUSH_CHANNEL_ID = "push_sync"

internal const val PUSH_NOTIFICATION_ID = 4201

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

@Composable
actual fun rememberBackgroundSyncScheduler(): BackgroundSyncScheduler? {
    val context = LocalContext.current.applicationContext
    return remember(context) { WorkManagerBackgroundSync(context) }
}

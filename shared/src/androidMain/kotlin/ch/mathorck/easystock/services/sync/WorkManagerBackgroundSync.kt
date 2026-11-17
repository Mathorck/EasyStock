package ch.mathorck.easystock.services.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import ch.mathorck.easystock.composables.sync.BACKGROUND_SYNC_WORK
import ch.mathorck.easystock.composables.sync.PUSH_SYNC_WORK
import ch.mathorck.easystock.composables.sync.backgroundSyncRequest

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

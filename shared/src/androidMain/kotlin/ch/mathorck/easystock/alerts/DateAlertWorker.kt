package ch.mathorck.easystock.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ch.mathorck.easystock.data.db.appDatabase
import ch.mathorck.easystock.services.alerts.AndroidDateAlertNotifications
import ch.mathorck.easystock.services.alerts.DateAlertNotifier
import ch.mathorck.easystock.services.alerts.RoomDateAlertStore

/** One past-date check. Local only (no network, no session): works in standalone and server mode. */
class DateAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        DateAlertNotifier(RoomDateAlertStore(appDatabase(applicationContext)), AndroidDateAlertNotifications(applicationContext)).check()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // A database or notification failure: the next period tries again
        Result.retry()
    }
}

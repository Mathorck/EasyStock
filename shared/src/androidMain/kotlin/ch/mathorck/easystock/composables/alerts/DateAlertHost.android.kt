package ch.mathorck.easystock.composables.alerts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ch.mathorck.easystock.alerts.DateAlertWorker
import java.util.concurrent.TimeUnit

internal const val DATE_ALERT_WORK = "easystock-date-alerts"

/** Hours between two checks: once a day is enough, a date is past from the day after. */
private const val DATE_ALERT_PERIOD_HOURS = 24L

/** The request of the job: every 24 hours, no constraint (it reads the local database). */
internal fun dateAlertRequest() = PeriodicWorkRequestBuilder<DateAlertWorker>(DATE_ALERT_PERIOD_HOURS, TimeUnit.HOURS).build()

@Composable
actual fun DateAlertHost() {
    val context = LocalContext.current.applicationContext
    // KEEP: a job already there stays untouched, so the daily time doesn't move at each launch
    LaunchedEffect(context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DATE_ALERT_WORK, ExistingPeriodicWorkPolicy.KEEP, dateAlertRequest())
    }
}

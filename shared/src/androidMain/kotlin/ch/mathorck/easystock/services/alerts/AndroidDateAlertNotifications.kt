package ch.mathorck.easystock.services.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.mathorck.easystock.data.model.attributes.PastDate
import ch.mathorck.easystock.shared.R
import ch.mathorck.easystock.viewmodels.alerts.mergeDateAlertLines
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.date_alerts_channel_description
import easystock.shared.generated.resources.date_alerts_channel_name
import easystock.shared.generated.resources.date_alerts_line
import org.jetbrains.compose.resources.getString

internal const val DATE_ALERTS_CHANNEL_ID = "date_alerts"

/** Its own id (the low-stock notifications use 1), so each kind is cancelled apart from the other. */
internal const val DATE_ALERTS_NOTIFICATION_ID = 2

private const val EXTRA_DATE_LINES = "ch.mathorck.easystock.alerts.DATE_LINES"

/** The "Dates passed" channel and its one notification. */
internal class AndroidDateAlertNotifications(context: Context) : DateAlertNotifications {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    override suspend fun show(dates: List<PastDate>): Boolean {
        if (dates.isEmpty()) return false
        // Same rule as the low-stock alerts: nothing is shown without the permission (Android 13+)
        if (!manager.areNotificationsEnabled()) return false
        val system = app.getSystemService(NotificationManager::class.java) ?: return false
        val title = getString(Res.string.date_alerts_channel_name)
        val channel = NotificationChannel(DATE_ALERTS_CHANNEL_ID, title, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = getString(Res.string.date_alerts_channel_description)
        system.createNotificationChannel(channel)
        // The user turned the channel off: nothing is shown, so the dates are not marked as notified
        if (system.getNotificationChannel(DATE_ALERTS_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        // The id is fixed: a new run would replace the lines the user has not seen yet, so they are kept
        val previous = runCatching { system.activeNotifications.toList() }.getOrDefault(emptyList())
            .firstOrNull { it.id == DATE_ALERTS_NOTIFICATION_ID && it.notification.channelId == DATE_ALERTS_CHANNEL_ID }
            ?.notification?.extras?.getStringArray(EXTRA_DATE_LINES).orEmpty().toList()
        val lines = mergeDateAlertLines(previous, dates.map { getString(Res.string.date_alerts_line, it.articleName, it.label, it.dateValue) })
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach(style::addLine)
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent()
        val pending = PendingIntent.getActivity(
            app, 0, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, DATE_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_low_stock)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setNumber(lines.size)
            .addExtras(Bundle().apply { putStringArray(EXTRA_DATE_LINES, lines.toTypedArray()) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(DATE_ALERTS_NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            // Permission denied: the dates stay un-notified and are tried again at the next run
            false
        }
    }
}

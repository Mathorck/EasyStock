package ch.mathorck.easystock.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.mathorck.easystock.shared.R
import ch.mathorck.easystock.util.formatQuantity
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.alerts_channel_description
import easystock.shared.generated.resources.alerts_channel_name
import easystock.shared.generated.resources.alerts_group_line
import easystock.shared.generated.resources.alerts_group_title
import easystock.shared.generated.resources.alerts_single_text
import easystock.shared.generated.resources.alerts_single_title
import org.jetbrains.compose.resources.getString

private const val CHANNEL_ID = "low_stock"

/** Same id for every alert notification: the tag (article id or [GROUP_KEY]) tells them apart. */
private const val NOTIFICATION_ID = 1

private const val EXTRA_ARTICLE_IDS = "ch.mathorck.easystock.alerts.ARTICLE_IDS"
private const val EXTRA_TARGET_ARTICLE = "ch.mathorck.easystock.alerts.OPEN_ARTICLE"
private const val EXTRA_TARGET_LOW_STOCK = "ch.mathorck.easystock.alerts.OPEN_LOW_STOCK"

/** The "Low stock" channel and its notifications. */
internal class AndroidAlertNotifications(context: Context) : AlertNotifications {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    override suspend fun createChannel() = ensureChannel()

    override suspend fun shown(): List<ShownAlert> {
        val system = app.getSystemService(NotificationManager::class.java) ?: return emptyList()
        return runCatching { system.activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { it.id == NOTIFICATION_ID && it.tag != null && it.notification.channelId == CHANNEL_ID }
            .map { ShownAlert(it.tag, it.notification.extras.getStringArray(EXTRA_ARTICLE_IDS).orEmpty().toSet()) }
    }

    override suspend fun show(notice: AlertNotice) {
        val intent = openIntent(notice.articleId).putExtra(EXTRA_TARGET_ARTICLE, notice.articleId)
        val builder = builder(listOf(notice), intent)
            .setContentTitle(getString(Res.string.alerts_single_title, notice.articleName))
            .setContentText(getString(Res.string.alerts_single_text, formatQuantity(notice.stockMilli), notice.unit))
        post(notice.articleId, builder)
    }

    override suspend fun showGroup(notices: List<AlertNotice>) {
        val intent = openIntent(GROUP_KEY).putExtra(EXTRA_TARGET_LOW_STOCK, true)
        val lines = notices.map { getString(Res.string.alerts_group_line, it.articleName, formatQuantity(it.stockMilli), it.unit) }
        val title = getString(Res.string.alerts_group_title, notices.size)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach(style::addLine)
        val builder = builder(notices, intent)
            .setContentTitle(title)
            .setContentText(notices.joinToString(", ") { it.articleName })
            .setNumber(notices.size)
            .setStyle(style)
        post(GROUP_KEY, builder)
    }

    override fun cancel(key: String) = manager.cancel(key, NOTIFICATION_ID)

    override fun cancelAll() {
        val system = app.getSystemService(NotificationManager::class.java) ?: return
        runCatching { system.activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { it.id == NOTIFICATION_ID && it.notification.channelId == CHANNEL_ID }
            .forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
    }

    private suspend fun builder(notices: List<AlertNotice>, intent: Intent): NotificationCompat.Builder {
        ensureChannel()
        val pending = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_low_stock)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .addExtras(Bundle().apply { putStringArray(EXTRA_ARTICLE_IDS, notices.map { it.articleId }.toTypedArray()) })
    }

    /** Created (or renamed after a language change) before each notification. */
    private suspend fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(Res.string.alerts_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = getString(Res.string.alerts_channel_description)
        app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun post(tag: String, builder: NotificationCompat.Builder) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(tag, NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission denied (Android 13+): the alert is still marked as seen
        }
    }

    /**
     * Opens the app (its launcher activity, on top of the running one). The data URI makes each
     * notification its own PendingIntent: the extras alone don't tell PendingIntents apart.
     */
    private fun openIntent(key: String): Intent {
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent()
        return launch
            .setData(Uri.Builder().scheme("easystock-alert").authority("open").appendPath(key).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/**
 * Reads a tapped alert notification from the intent of the activity (`MainActivity.onCreate`
 * and `onNewIntent`), then removes it from the intent so a recreated activity doesn't open it again.
 */
fun handleAlertIntent(intent: Intent?) {
    intent ?: return
    val articleId = intent.getStringExtra(EXTRA_TARGET_ARTICLE)
    val lowStock = intent.getBooleanExtra(EXTRA_TARGET_LOW_STOCK, false)
    when {
        articleId != null -> AlertNavigation.open(AlertTarget.Article(articleId))
        lowStock -> AlertNavigation.open(AlertTarget.LowStock)
        else -> return
    }
    intent.removeExtra(EXTRA_TARGET_ARTICLE)
    intent.removeExtra(EXTRA_TARGET_LOW_STOCK)
}

@Volatile
private var notifications: AndroidAlertNotifications? = null

/** The notifications of the process. */
fun alertNotifications(context: Context): AlertNotifications = notifications ?: synchronized(AndroidAlertNotifications::class) {
    notifications ?: AndroidAlertNotifications(context).also { notifications = it }
}

@Composable
actual fun rememberAlertNotifications(): AlertNotifications? = alertNotifications(LocalContext.current)

@Composable
actual fun rememberNotificationPermission(): NotificationPermission? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val context = LocalContext.current
    // The answer changes nothing: alerts are marked as seen either way
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(context, launcher) {
        object : NotificationPermission {
            override val canRequest: Boolean
                get() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

            override fun request() = launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

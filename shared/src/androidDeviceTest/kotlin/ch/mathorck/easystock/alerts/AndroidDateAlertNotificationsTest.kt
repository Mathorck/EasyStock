package ch.mathorck.easystock.alerts

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.data.model.PastDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidDateAlertNotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dateNotifications = AndroidDateAlertNotifications(context)
    private val lowStock = AndroidAlertNotifications(context)
    private val system = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() {
        system.cancel(DATE_ALERTS_NOTIFICATION_ID)
        lowStock.cancelAll()
    }

    private fun past(article: String) = PastDate("a-$article", "Article $article", "a-$article", false, "expiry", "Expiry", "2026-01-01", true)

    /** The system lists a posted or cancelled notification a moment later. */
    private suspend fun awaitDateLines(done: (Int) -> Boolean): Int {
        repeat(40) {
            val count = dateLines()
            if (done(count)) return count
            delay(50)
        }
        return dateLines()
    }

    private fun dateLines(): Int = system.activeNotifications
        .firstOrNull { it.id == DATE_ALERTS_NOTIFICATION_ID && it.notification.channelId == DATE_ALERTS_CHANNEL_ID }
        ?.notification?.extras?.getStringArray("ch.mathorck.easystock.alerts.DATE_LINES")?.size ?: 0

    @Test
    fun lowStockCancelAllKeepsTheDateNotification() = runBlocking<Unit> {
        assertTrue(dateNotifications.show(listOf(past("1"))))
        assertEquals(1, awaitDateLines { it == 1 })
        lowStock.cancelAll()
        delay(300)
        assertEquals(1, dateLines())
    }

    @Test
    fun aNewRunKeepsTheLinesNotSeenYet() = runBlocking<Unit> {
        assertTrue(dateNotifications.show(listOf(past("1"))))
        assertEquals(1, awaitDateLines { it == 1 })
        assertTrue(dateNotifications.show(listOf(past("2"))))
        assertEquals(2, awaitDateLines { it == 2 })
    }
}

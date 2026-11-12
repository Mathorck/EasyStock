package ch.mathorck.easystock.alerts

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidAlertNotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = AndroidAlertNotifications(context)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() = notifications.cancelAll()

    /** The system lists a posted or cancelled notification a moment later. */
    private suspend fun awaitShown(done: (List<ShownAlert>) -> Boolean): List<ShownAlert> {
        repeat(40) {
            val shown = notifications.shown()
            if (done(shown)) return shown
            delay(50)
        }
        return notifications.shown()
    }

    private fun notice(id: String) = AlertNotice(id, "Article $id", 1500, "kg")

    @Test
    fun shownNotificationsKeepTheirArticles() = runBlocking<Unit> {
        notifications.show(notice("a1"))
        notifications.showGroup(listOf(notice("a2"), notice("a3"), notice("a4"), notice("a5")))
        val shown = awaitShown { it.size == 2 }.associate { it.key to it.articleIds }
        assertEquals(setOf("a1"), shown["a1"])
        assertEquals(setOf("a2", "a3", "a4", "a5"), shown[GROUP_KEY])

        notifications.cancel("a1")
        assertEquals(setOf(GROUP_KEY), awaitShown { it.size == 1 }.map { it.key }.toSet())
        notifications.cancelAll()
        assertTrue(awaitShown { it.isEmpty() }.isEmpty())
    }
}

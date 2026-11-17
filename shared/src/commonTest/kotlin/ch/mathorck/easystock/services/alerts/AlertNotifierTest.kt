package ch.mathorck.easystock.services.alerts

import ch.mathorck.easystock.data.model.alerts.LowStockAlert
import ch.mathorck.easystock.data.model.article.ArticleStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class AlertNotifierTest {
    private class FakeStore(var role: String?, val alerts: MutableList<LowStockAlert>, val articles: List<ArticleStock>) : AlertStore {
        var reads = 0
        override suspend fun storedRole() = role
        override suspend fun pending(): List<LowStockAlert> {
            reads++
            return alerts.filter { !it.notified }
        }
        override suspend fun articleStock(ids: Collection<String>) = articles.filter { it.article.id in ids }
        override suspend fun markNotified(ids: Collection<String>) {
            alerts.replaceAll { if (it.id in ids) it.copy(notified = true) else it }
        }
    }

    private class FakeNotifications : AlertNotifications {
        val posted = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        override suspend fun createChannel() = Unit
        override suspend fun shown() = emptyList<ShownAlert>()
        override suspend fun show(notice: AlertNotice) {
            posted += notice.articleId
        }
        override suspend fun showGroup(notices: List<AlertNotice>) {
            posted += GROUP_KEY
        }
        override fun cancel(key: String) {
            cancelled += key
        }
        override fun cancelAll() = Unit
    }

    private val clock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(NOW)
    }

    @Test
    fun standaloneReadsNothingAndShowsNothing() = runTest {
        val store = FakeStore(null, mutableListOf(alert("al1", "a1")), listOf(stock("a1", 0)))
        val notifications = FakeNotifications()
        AlertNotifier(store, notifications, clock).check()
        assertEquals(0, store.reads)
        assertTrue(notifications.posted.isEmpty())
        assertTrue(store.alerts.none { it.notified })
    }

    @Test
    fun anAlertIsShownOnlyOnce() = runTest {
        val store = FakeStore("editor", mutableListOf(alert("al1", "a1")), listOf(stock("a1", 0)))
        val notifications = FakeNotifications()
        val notifier = AlertNotifier(store, notifications, clock)
        notifier.check()
        notifier.check()
        assertEquals(listOf("a1"), notifications.posted)
        assertTrue(store.alerts.all { it.notified })
    }

    @Test
    fun aGuestNeverGetsAlerts() = runTest {
        val store = FakeStore("guest", mutableListOf(alert("al1", "a1")), listOf(stock("a1", 0)))
        val notifications = FakeNotifications()
        AlertNotifier(store, notifications, clock).check()
        assertTrue(notifications.posted.isEmpty())
        assertTrue(store.alerts.all { it.notified })
    }

    @Test
    fun anUnknownRoleGetsTheGuestRights() = runTest {
        val store = FakeStore("owner", mutableListOf(alert("al1", "a1")), listOf(stock("a1", 0)))
        val notifications = FakeNotifications()
        AlertNotifier(store, notifications, clock).check()
        assertTrue(notifications.posted.isEmpty())
    }

    @Test
    fun aViewerGetsTheAlertsTheServerSends() = runTest {
        // The server filters the Viewers who did not turn alerts on (pull time)
        val store = FakeStore("viewer", mutableListOf(alert("al1", "a1")), listOf(stock("a1", 0)))
        val notifications = FakeNotifications()
        AlertNotifier(store, notifications, clock).check()
        assertEquals(listOf("a1"), notifications.posted)
    }
}

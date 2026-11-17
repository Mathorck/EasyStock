package ch.mathorck.easystock.services.alerts

import ch.mathorck.easystock.data.model.attributes.PastDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class DateAlertNotifierTest {
    private fun past(target: String, notify: Boolean = true, value: String = "2026-01-01") =
        PastDate("art-$target", "Article $target", target, false, "expiry", "Expiry", value, notify)

    private class FakeStore(
        val past: List<PastDate>,
        var notified: Set<Triple<String, String, String>> = emptySet(),
        val current: Map<Pair<String, String>, String> = past.associate { (it.targetId to it.attributeKey) to it.dateValue },
    ) : DateAlertStore {
        var requestedToday: LocalDate? = null
        var markedAt: Long? = null
        var pruned: Set<Triple<String, String, String>> = emptySet()

        override suspend fun pastDates(today: LocalDate): List<PastDate> {
            requestedToday = today
            return past
        }

        override suspend fun notified() = notified

        override suspend fun currentDates() = current

        override suspend fun prune(stale: Set<Triple<String, String, String>>) {
            pruned = stale
            notified = notified - stale
        }

        override suspend fun markNotified(dates: List<PastDate>, now: Long) {
            markedAt = now
            notified = notified + dates.map { Triple(it.targetId, it.attributeKey, it.dateValue) }
        }
    }

    private class FakeNotifications(val works: Boolean = true) : DateAlertNotifications {
        val shown = mutableListOf<List<PastDate>>()

        override suspend fun show(dates: List<PastDate>): Boolean {
            shown += dates
            return works
        }
    }

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-18T10:00:00Z")
    }

    @Test
    fun notifiesOnceThenNeverAgainForTheSameDate() = runTest {
        val store = FakeStore(listOf(past("a1"), past("a2", notify = false)))
        val notifications = FakeNotifications()
        val notifier = DateAlertNotifier(store, notifications, clock)
        notifier.check()
        notifier.check()
        assertEquals(1, notifications.shown.size)
        assertEquals(listOf("a1"), notifications.shown.single().map { it.targetId })
        assertEquals(clock.now().toEpochMilliseconds(), store.markedAt)
    }

    @Test
    fun aRefusedNotificationIsNotMarkedSoItComesLater() = runTest {
        val store = FakeStore(listOf(past("a1")))
        DateAlertNotifier(store, FakeNotifications(works = false), clock).check()
        assertTrue(store.notified.isEmpty())
    }

    @Test
    fun nothingDueShowsNothing() = runTest {
        val notifications = FakeNotifications()
        DateAlertNotifier(FakeStore(emptyList()), notifications, clock).check()
        assertTrue(notifications.shown.isEmpty())
    }

    @Test
    fun noticesOfChangedOrGoneDatesArePrunedButNotOfAFlagTurnedOff() = runTest {
        val store = FakeStore(
            past = emptyList(),
            notified = setOf(
                Triple("a1", "expiry", "2025-05-05"), // date changed
                Triple("gone", "expiry", "2026-01-01"), // deleted or archived
                Triple("a2", "expiry", "2026-01-01"), // flag off: same date, kept
            ),
            current = mapOf(("a1" to "expiry") to "2027-01-01", ("a2" to "expiry") to "2026-01-01"),
        )
        DateAlertNotifier(store, FakeNotifications(), clock).check()
        assertEquals(setOf(Triple("a1", "expiry", "2025-05-05"), Triple("gone", "expiry", "2026-01-01")), store.pruned)
    }
}

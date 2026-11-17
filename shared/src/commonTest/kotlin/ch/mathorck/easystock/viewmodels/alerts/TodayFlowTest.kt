package ch.mathorck.easystock.viewmodels.alerts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class TodayFlowTest {
    private val utc = TimeZone.UTC

    @Test
    fun waitsUntilTheNextLocalMidnight() {
        assertEquals(3_600_000L, millisUntilNextDay(Instant.parse("2026-11-18T23:00:00Z"), utc))
        // At midnight sharp, the next one is a full day away
        assertEquals(86_400_000L, millisUntilNextDay(Instant.parse("2026-11-18T00:00:00Z"), utc))
        // Local time, not UTC: 22:00 UTC is 23:00 in Zurich (UTC+1 in November)
        assertEquals(3_600_000L, millisUntilNextDay(Instant.parse("2026-11-18T22:00:00Z"), TimeZone.of("Europe/Zurich")))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun emitsTodayThenEachNewDay() = runTest {
        val start = Instant.parse("2026-11-18T22:30:00Z").toEpochMilliseconds()
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(start + testScheduler.currentTime)
        }
        val days = todayFlow(clock, utc).take(3).toList()
        assertEquals(listOf(LocalDate(2026, 11, 18), LocalDate(2026, 11, 19), LocalDate(2026, 11, 20)), days)
    }
}

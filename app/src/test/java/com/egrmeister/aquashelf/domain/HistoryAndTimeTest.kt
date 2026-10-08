package com.egrmeister.aquashelf.domain

import com.egrmeister.aquashelf.FakeEntryStore
import com.egrmeister.aquashelf.FakeTime
import com.egrmeister.aquashelf.domain.entries.AmountResult
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.EntryService
import com.egrmeister.aquashelf.domain.entries.EntryTimeResult
import com.egrmeister.aquashelf.domain.entries.EntryValidator
import com.egrmeister.aquashelf.domain.history.WeekHistory
import com.egrmeister.aquashelf.domain.time.TimeMath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryAndTimeTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun week_isTodayAndPreviousSixDates_newestFirst() {
        val r = WeekHistory.range(today, 0)
        assertEquals(LocalDate.of(2026, 10, 2), r.start)
        assertEquals(today, r.end)
        assertEquals(7, r.daysNewestFirst.size)
        assertEquals(today, r.daysNewestFirst.first())
        val earlier = WeekHistory.range(today, 1)
        assertEquals(LocalDate.of(2026, 9, 25), earlier.start)
        assertEquals(LocalDate.of(2026, 10, 1), earlier.end)
    }

    @Test
    fun weekBoundaries_excludeEntriesOutsideRange() {
        val r = WeekHistory.range(today, 0)
        val inside = entry(1, 250, ContainerType.GLASS, LocalTime.NOON, date = r.start)
        val outside = entry(2, 250, ContainerType.GLASS, LocalTime.NOON, date = r.start.minusDays(1))
        val days = WeekHistory.summarize(today, r, listOf(inside, outside))
        assertEquals(1, days.sumOf { it.totals.entries })
        assertEquals(1, days.last().totals.entries)
    }

    @Test
    fun preview_showsSixObjects_plusNMore_andEmptyDaysSayNoRecords() {
        val r = WeekHistory.range(today, 0)
        val entries = (1L..9L).map { entry(it, 100, ContainerType.GLASS, LocalTime.of(8, 0).plusMinutes(it), date = today) }
        val days = WeekHistory.summarize(today, r, entries)
        val t = days.first()
        assertTrue(t.isToday)
        assertEquals(6, t.preview.size)
        assertEquals(3, t.moreCount)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), t.preview.map { it.id })
        assertFalse(days[1].hasRecords)
        assertEquals(0, days[1].totals.entries)
    }

    @Test
    fun midnight_isComputedInTheCurrentZone() {
        val zone = ZoneId.of("Europe/Minsk")
        val now = LocalDateTime.of(2026, 10, 8, 23, 59, 30).atZone(zone).toInstant()
        assertEquals(30_000L, TimeMath.millisUntilNextMidnight(now, zone))
        val ny = ZoneId.of("America/New_York")
        // Day before the autumn DST change still ends at local midnight.
        val beforeDst = LocalDateTime.of(2026, 10, 31, 23, 0).atZone(ny).toInstant()
        assertEquals(3_600_000L, TimeMath.millisUntilNextMidnight(beforeDst, ny))
    }

    @Test
    fun travel_doesNotMoveSavedEntriesBetweenDays() = runTest {
        val minsk = ZoneId.of("Europe/Minsk")
        val time = FakeTime(LocalDateTime.of(2026, 10, 8, 1, 30).atZone(minsk).toInstant(), minsk)
        val store = FakeEntryStore()
        val service = EntryService(store, time)
        val e = service.logNow(250, ContainerType.GLASS, null)
        // Fly to New York: the same instant is still Oct 7 there, but the entry keeps its saved date.
        time.zoneId = ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026, 10, 7), time.today())
        assertEquals(LocalDate.of(2026, 10, 8), store.byId(e.id)!!.localDate)
        assertEquals(1, store.on(LocalDate.of(2026, 10, 8)).size)
    }

    @Test
    fun manualTime_rejectsFutureAndNonexistentDstTimes_andResolvesOverlapConsistently() {
        val ny = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 11, 2, 12, 0).atZone(ny).toInstant()

        val future = EntryValidator.resolve(LocalDate.of(2026, 11, 2), LocalTime.of(12, 30), ny, now)
        assertTrue(future is EntryTimeResult.Invalid)
        val tomorrow = EntryValidator.resolve(LocalDate.of(2026, 11, 3), LocalTime.of(1, 0), ny, now)
        assertTrue(tomorrow is EntryTimeResult.Invalid)

        // 2026-03-08 02:30 does not exist in New York (spring forward).
        val gap = EntryValidator.resolve(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ny, now)
        assertTrue(gap is EntryTimeResult.Invalid)
        assertTrue((gap as EntryTimeResult.Invalid).message.contains("doesn't exist"))

        // 2026-11-01 01:30 happens twice (fall back): always the earlier offset (-04:00).
        val overlap = EntryValidator.resolve(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), ny, now)
        assertEquals(ZoneOffset.ofHours(-4), (overlap as EntryTimeResult.Valid).zoned.offset)

        val past = EntryValidator.resolve(LocalDate.of(2026, 11, 2), LocalTime.of(11, 59), ny, now)
        assertEquals(Instant.parse("2026-11-02T16:59:00Z"), (past as EntryTimeResult.Valid).zoned.toInstant())
    }

    @Test
    fun amount_wholeMillilitersFrom1To5000() {
        assertEquals(AmountResult.Valid(1), EntryValidator.parseAmount("1"))
        assertEquals(AmountResult.Valid(5000), EntryValidator.parseAmount("5,000"))
        assertTrue(EntryValidator.parseAmount("0") is AmountResult.Invalid)
        assertTrue(EntryValidator.parseAmount("5001") is AmountResult.Invalid)
        assertTrue(EntryValidator.parseAmount("250.5") is AmountResult.Invalid)
        assertTrue(EntryValidator.parseAmount("") is AmountResult.Invalid)
        assertTrue(EntryValidator.parseAmount("99999999999999999999") is AmountResult.Invalid)
    }
}

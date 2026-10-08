package com.egrmeister.aquashelf.domain.history

import com.egrmeister.aquashelf.domain.entries.DayTotals
import com.egrmeister.aquashelf.domain.entries.ShelfOrder
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import java.time.LocalDate

/** One miniature shelf in the week view. [preview] never hides access: the day opens the full shelf. */
data class DaySummary(
    val date: LocalDate,
    val totals: DayTotals,
    val preview: List<WaterEntry>,
    val moreCount: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    val hasRecords: Boolean get() = totals.entries > 0
}

data class DateRange(val start: LocalDate, val end: LocalDate) {
    /** Newest first. */
    val daysNewestFirst: List<LocalDate>
        get() = generateSequence(end) { it.minusDays(1) }.takeWhile { !it.isBefore(start) }.toList()

    fun contains(d: LocalDate): Boolean = !d.isBefore(start) && !d.isAfter(end)
}

object WeekHistory {
    const val DAYS = 7
    const val PREVIEW = 6

    /** Page 0 = today and the previous six dates; page n = the seven-day range n weeks earlier. */
    fun range(today: LocalDate, page: Int): DateRange {
        val end = today.minusDays(DAYS.toLong() * page.coerceAtLeast(0))
        return DateRange(end.minusDays((DAYS - 1).toLong()), end)
    }

    /** Groups by each entry's saved local date. Days without entries are "No records", never zero claims. */
    fun summarize(today: LocalDate, range: DateRange, entries: List<WaterEntry>): List<DaySummary> {
        val byDate = entries.filter { range.contains(it.localDate) }.groupBy { it.localDate }
        return range.daysNewestFirst.map { date ->
            val day = ShelfOrder.arrange(byDate[date].orEmpty())
            DaySummary(
                date = date,
                totals = DayTotals.of(day),
                preview = day.take(PREVIEW),
                moreCount = (day.size - PREVIEW).coerceAtLeast(0),
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }
}

package com.egrmeister.aquashelf.domain.entries

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** English, mL-only formatting used across the app. */
object Fmt {
    private val TIME_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val TIME_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun ml(value: Long): String = String.format(Locale.US, "%,d mL", value)
    fun ml(value: Int): String = ml(value.toLong())

    fun time(t: LocalTime, use24h: Boolean): String = t.format(if (use24h) TIME_24 else TIME_12)

    fun minuteOfDay(minute: Int, use24h: Boolean): String =
        time(LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60), use24h)

    fun shortDay(d: LocalDate): String = d.format(SHORT_DAY)
    fun longDay(d: LocalDate): String = d.format(LONG_DAY)
    fun weekday(d: LocalDate): String = d.format(WEEKDAY)
    fun monthDay(d: LocalDate): String = d.format(MONTH_DAY)

    fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> shortDay(d)
    }

    /** "Glass, 250 mL, 8:15 AM" (+ preset name) — the accessible label of one shelf object. */
    fun entryLabel(e: WaterEntry, use24h: Boolean): String = buildString {
        append(e.type.label).append(", ").append(ml(e.amountMl)).append(", ").append(time(e.localTime, use24h))
        e.presetNameSnapshot?.let { append(", preset ").append(it) }
    }

    /** "250 mL glass" — used in confirmations and the Undo label. */
    fun entryShort(e: WaterEntry): String = when (e.type) {
        ContainerType.CUSTOM -> "${ml(e.amountMl)} custom entry"
        else -> "${ml(e.amountMl)} ${e.type.singular}"
    }
}

package com.egrmeister.aquashelf.domain.time

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Injectable clock + time-zone provider so tests control "now" and the zone. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

object TimeMath {
    /** Time until the next local midnight in [zone] (start of the next day, DST-aware). */
    fun millisUntilNextMidnight(now: Instant, zone: ZoneId): Long {
        val nextDay = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, nextDay).toMillis().coerceAtLeast(1L)
    }
}

/**
 * Publishes the current local date. Refreshes at midnight, when the app returns to the
 * foreground ([refresh]) and after time / time-zone changes. Never touches stored data.
 */
class TodayProvider(private val time: TimeSource, scope: CoroutineScope) {
    private val _today = MutableStateFlow(time.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                // Capped so a time-zone change while the app is open self-corrects quickly.
                val wait = TimeMath.millisUntilNextMidnight(time.now(), time.zone()).coerceAtMost(MAX_WAIT_MS)
                delay(wait + 250)
                refresh()
            }
        }
    }

    fun refresh() {
        _today.value = time.today()
    }

    fun current(): LocalDate = _today.value

    private companion object {
        const val MAX_WAIT_MS = 15 * 60 * 1000L
    }
}

package com.egrmeister.aquashelf.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Optional local reminders. Disabled by default; same-day windows only. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 21 * 60,
    val intervalMinutes: Int = 120,
) {
    val isValid: Boolean
        get() = startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in 0 until MINUTES_PER_DAY &&
            endMinute > startMinute &&
            intervalMinutes in INTERVALS

    companion object {
        val INTERVALS = listOf(60, 90, 120, 180)
        const val MINUTES_PER_DAY = 24 * 60

        fun windowError(startMinute: Int, endMinute: Int): String? =
            if (endMinute <= startMinute) "End time must be later than start time (same day)." else null
    }
}

data class ReminderTarget(val date: LocalDate, val minuteOfDay: Int, val triggerAt: Instant)

enum class ReminderDecision {
    POST,
    SKIP_DISABLED,
    SKIP_NOTIFICATIONS_BLOCKED,
    SKIP_INVALID_SETTINGS,
    SKIP_STALE,
    SKIP_TOO_EARLY,
    SKIP_RECENTLY_LOGGED,
}

object ReminderPlanner {
    /** A delivered alarm may be up to this early (inexact batching) and still count. */
    private const val EARLY_TOLERANCE_MINUTES = 2

    /** Boundaries in [start, end): start, start + interval, ... */
    fun boundaries(s: ReminderSettings): List<Int> {
        if (!s.isValid) return emptyList()
        return generateSequence(s.startMinute) { it + s.intervalMinutes }.takeWhile { it < s.endMinute }.toList()
    }

    private fun at(date: LocalDate, minute: Int, zone: ZoneId): Instant =
        // ZonedDateTime.of moves a time inside a DST gap forward — fine for an inexact reminder.
        ZonedDateTime.of(LocalDateTime.of(date, LocalTime.MIDNIGHT).plusMinutes(minute.toLong()), zone).toInstant()

    /** Next boundary strictly after [now] today, else the first one tomorrow. Uses the CURRENT zone. */
    fun nextTarget(now: ZonedDateTime, s: ReminderSettings): ReminderTarget? {
        if (!s.enabled) return null
        val points = boundaries(s)
        if (points.isEmpty()) return null
        val zone = now.zone
        val today = now.toLocalDate()
        for (date in listOf(today, today.plusDays(1))) {
            for (m in points) {
                val trigger = at(date, m, zone)
                if (trigger.isAfter(now.toInstant())) return ReminderTarget(date, m, trigger)
            }
        }
        return null
    }

    /**
     * Final check right before posting:
     * reminders enabled, notifications deliverable, settings still contain this boundary, still the
     * intended date, still inside this boundary's slot (otherwise stale — no backlog), and nothing
     * was logged since the previous scheduled boundary.
     */
    fun decide(
        settings: ReminderSettings,
        canDeliver: Boolean,
        targetDate: LocalDate,
        targetMinute: Int,
        now: ZonedDateTime,
        lastLoggedAt: Instant?,
    ): ReminderDecision {
        if (!settings.enabled) return ReminderDecision.SKIP_DISABLED
        if (!canDeliver) return ReminderDecision.SKIP_NOTIFICATIONS_BLOCKED
        if (!settings.isValid) return ReminderDecision.SKIP_INVALID_SETTINGS
        if (targetMinute !in boundaries(settings)) return ReminderDecision.SKIP_STALE
        if (now.toLocalDate() != targetDate) return ReminderDecision.SKIP_STALE
        val nowMinute = now.hour * 60 + now.minute
        if (nowMinute < targetMinute - EARLY_TOLERANCE_MINUTES) return ReminderDecision.SKIP_TOO_EARLY
        val slotEnd = minOf(targetMinute + settings.intervalMinutes, settings.endMinute)
        if (nowMinute >= slotEnd) return ReminderDecision.SKIP_STALE
        val previousBoundary = at(targetDate, targetMinute - settings.intervalMinutes, now.zone)
        if (lastLoggedAt != null && !lastLoggedAt.isBefore(previousBoundary)) return ReminderDecision.SKIP_RECENTLY_LOGGED
        return ReminderDecision.POST
    }
}

enum class ReminderStatus { OFF, ACTIVE, NEEDS_PERMISSION, BLOCKED }

/** Pure description of notification availability so the UI, the scheduler and tests agree. */
data class NotificationAccess(
    val runtimePermissionRequired: Boolean,
    val permissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val channelEnabled: Boolean,
    val alreadyPrompted: Boolean,
) {
    val canDeliver: Boolean
        get() = (!runtimePermissionRequired || permissionGranted) && appNotificationsEnabled && channelEnabled

    /** Ask the system at most once; afterwards only explain and offer the settings link. */
    val shouldRequestPermission: Boolean
        get() = runtimePermissionRequired && !permissionGranted && !alreadyPrompted

    fun status(remindersEnabled: Boolean): ReminderStatus = when {
        !remindersEnabled -> ReminderStatus.OFF
        canDeliver -> ReminderStatus.ACTIVE
        shouldRequestPermission -> ReminderStatus.NEEDS_PERMISSION
        else -> ReminderStatus.BLOCKED
    }
}

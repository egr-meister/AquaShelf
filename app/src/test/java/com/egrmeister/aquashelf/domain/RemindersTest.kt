package com.egrmeister.aquashelf.domain

import com.egrmeister.aquashelf.domain.reminders.NotificationAccess
import com.egrmeister.aquashelf.domain.reminders.ReminderDecision
import com.egrmeister.aquashelf.domain.reminders.ReminderPlanner
import com.egrmeister.aquashelf.domain.reminders.ReminderSettings
import com.egrmeister.aquashelf.domain.reminders.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val zone = ZoneId.of("Europe/Minsk")
    private val day = LocalDate.of(2026, 10, 8)
    private val on = ReminderSettings(enabled = true, startMinute = 9 * 60, endMinute = 21 * 60, intervalMinutes = 120)

    private fun t(h: Int, m: Int, date: LocalDate = day): ZonedDateTime = LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone)

    @Test
    fun defaults_areDisabled_andWindowMustBeSameDayWithEndAfterStart() {
        assertFalse(ReminderSettings().enabled)
        assertFalse(ReminderSettings(true, 600, 600, 60).isValid)
        assertFalse(ReminderSettings(true, 1200, 300, 60).isValid)
        assertFalse(ReminderSettings(true, 540, 1260, 45).isValid)
        assertTrue(on.isValid)
        assertEquals(listOf(540, 660, 780, 900, 1020, 1140), ReminderPlanner.boundaries(on))
        assertEquals(listOf(540, 630, 720), ReminderPlanner.boundaries(on.copy(endMinute = 12 * 60 + 30, intervalMinutes = 90)))
    }

    @Test
    fun nextTarget_isNextBoundaryToday_elseFirstTomorrow_orNoneWhenDisabled() {
        val next = ReminderPlanner.nextTarget(t(10, 15), on)!!
        assertEquals(day, next.date)
        assertEquals(11 * 60, next.minuteOfDay)
        assertEquals(t(11, 0).toInstant(), next.triggerAt)

        val late = ReminderPlanner.nextTarget(t(20, 0), on)!!
        assertEquals(day.plusDays(1), late.date)
        assertEquals(9 * 60, late.minuteOfDay)

        assertNull(ReminderPlanner.nextTarget(t(10, 0), on.copy(enabled = false)))
        assertNull(ReminderPlanner.nextTarget(t(10, 0), on.copy(endMinute = 8 * 60)))
    }

    @Test
    fun nextTarget_usesTheCurrentTimeZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = LocalDateTime.of(day, java.time.LocalTime.of(10, 15)).atZone(tokyo)
        val next = ReminderPlanner.nextTarget(now, on)!!
        assertEquals(LocalDateTime.of(day, java.time.LocalTime.of(11, 0)).atZone(tokyo).toInstant(), next.triggerAt)
    }

    @Test
    fun decide_postsOnlyWhenEverythingIsStillValid() {
        assertEquals(ReminderDecision.POST, ReminderPlanner.decide(on, true, day, 660, t(11, 5), lastLoggedAt = t(8, 0).toInstant()))
        assertEquals(ReminderDecision.SKIP_DISABLED, ReminderPlanner.decide(on.copy(enabled = false), true, day, 660, t(11, 5), null))
        assertEquals(ReminderDecision.SKIP_NOTIFICATIONS_BLOCKED, ReminderPlanner.decide(on, false, day, 660, t(11, 5), null))
    }

    @Test
    fun decide_skipsStaleDelivery_noBacklog() {
        // Delivered after the next boundary (Doze) — stale.
        assertEquals(ReminderDecision.SKIP_STALE, ReminderPlanner.decide(on, true, day, 660, t(13, 1), null))
        // Delivered the next day.
        assertEquals(ReminderDecision.SKIP_STALE, ReminderPlanner.decide(on, true, day, 1140, t(9, 30, day.plusDays(1)), null))
        // Last slot ends at the window end.
        assertEquals(ReminderDecision.SKIP_STALE, ReminderPlanner.decide(on, true, day, 1140, t(21, 0), null))
        // Settings changed so this boundary no longer exists.
        assertEquals(ReminderDecision.SKIP_STALE, ReminderPlanner.decide(on.copy(intervalMinutes = 180), true, day, 660, t(11, 5), null))
        assertEquals(ReminderDecision.SKIP_TOO_EARLY, ReminderPlanner.decide(on, true, day, 660, t(10, 30), null))
    }

    @Test
    fun decide_suppressesWhenWaterWasLoggedSincePreviousBoundary() {
        // Previous boundary for 11:00 is 09:00.
        assertEquals(
            ReminderDecision.SKIP_RECENTLY_LOGGED,
            ReminderPlanner.decide(on, true, day, 660, t(11, 2), lastLoggedAt = t(9, 0).toInstant()),
        )
        assertEquals(
            ReminderDecision.SKIP_RECENTLY_LOGGED,
            ReminderPlanner.decide(on, true, day, 660, t(11, 2), lastLoggedAt = t(10, 59).toInstant()),
        )
        assertEquals(
            ReminderDecision.POST,
            ReminderPlanner.decide(on, true, day, 660, t(11, 2), lastLoggedAt = t(8, 59).toInstant()),
        )
    }

    @Test
    fun permissionDenied_neverRepromptsAndBlocksOnlyReminders() {
        val denied = NotificationAccess(
            runtimePermissionRequired = true,
            permissionGranted = false,
            appNotificationsEnabled = false,
            channelEnabled = true,
            alreadyPrompted = true,
        )
        assertFalse(denied.canDeliver)
        assertFalse("never prompt a second time", denied.shouldRequestPermission)
        assertEquals(ReminderStatus.BLOCKED, denied.status(remindersEnabled = true))
        assertEquals(ReminderStatus.OFF, denied.status(remindersEnabled = false))
        assertEquals(
            ReminderDecision.SKIP_NOTIFICATIONS_BLOCKED,
            ReminderPlanner.decide(on, denied.canDeliver, day, 660, t(11, 5), null),
        )

        val firstTime = denied.copy(alreadyPrompted = false)
        assertTrue(firstTime.shouldRequestPermission)
        assertEquals(ReminderStatus.NEEDS_PERMISSION, firstTime.status(true))

        val channelOff = NotificationAccess(true, true, true, channelEnabled = false, alreadyPrompted = true)
        assertFalse(channelOff.canDeliver)

        val legacy = NotificationAccess(false, false, true, true, false)
        assertTrue("Android 12 and lower need no runtime permission", legacy.canDeliver)
        assertFalse(legacy.shouldRequestPermission)
    }
}

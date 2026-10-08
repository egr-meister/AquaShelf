package com.egrmeister.aquashelf.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.egrmeister.aquashelf.data.local.PreferencesStore
import com.egrmeister.aquashelf.domain.entries.EntryService
import com.egrmeister.aquashelf.domain.reminders.ReminderDecision
import com.egrmeister.aquashelf.domain.reminders.ReminderPlanner
import com.egrmeister.aquashelf.domain.reminders.ReminderTarget
import com.egrmeister.aquashelf.domain.time.TimeSource
import java.time.LocalDate

/**
 * One inexact, one-shot alarm for the next reminder boundary. No exact-alarm permission,
 * no foreground service, no wake lock. Android may deliver late; [handleAlarm] re-checks everything
 * and silently drops stale reminders, then schedules the next one.
 */
class ReminderScheduler(
    private val context: Context,
    private val entries: EntryService,
    private val prefs: PreferencesStore,
    private val time: TimeSource,
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    suspend fun reschedule() {
        cancelAlarm()
        val settings = prefs.current().reminders
        val now = time.now().atZone(time.zone())
        val target = ReminderPlanner.nextTarget(now, settings) ?: return
        // AlarmManager.set() is inexact — exactly what the spec asks for.
        alarmManager?.set(AlarmManager.RTC_WAKEUP, target.triggerAt.toEpochMilli(), pendingIntent(target))
        Log.d(TAG, "Reminder scheduled for ${target.date} minute ${target.minuteOfDay}")
    }

    suspend fun handleAlarm(intent: Intent) {
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        if (date != null && minute >= 0) {
            val p = prefs.current()
            val decision = ReminderPlanner.decide(
                settings = p.reminders,
                canDeliver = Notifications.access(context, alreadyPrompted = true).canDeliver,
                targetDate = date,
                targetMinute = minute,
                now = time.now().atZone(time.zone()),
                lastLoggedAt = entries.latestLoggedAt(),
            )
            Log.d(TAG, "Reminder $date/$minute: $decision")
            if (decision == ReminderDecision.POST) Notifications.postReminder(context)
        }
        reschedule()
    }

    fun cancelAlarm() {
        alarmManager?.cancel(basePendingIntent())
    }

    /** Cancels the pending alarm and any visible reminder. */
    fun cancelAll() {
        cancelAlarm()
        Notifications.cancelReminder(context)
    }

    private fun baseIntent() = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)

    private fun basePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        baseIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pendingIntent(target: ReminderTarget): PendingIntent {
        val intent = baseIntent()
            .putExtra(EXTRA_DATE, target.date.toString())
            .putExtra(EXTRA_MINUTE, target.minuteOfDay)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "AquaShelfReminders"
        const val ACTION_REMIND = "com.egrmeister.aquashelf.action.REMIND"
        private const val REQUEST_CODE = 7
        private const val EXTRA_DATE = "date"
        private const val EXTRA_MINUTE = "minute_of_day"
    }
}

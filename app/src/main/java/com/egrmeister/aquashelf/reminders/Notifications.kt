package com.egrmeister.aquashelf.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.egrmeister.aquashelf.MainActivity
import com.egrmeister.aquashelf.R
import com.egrmeister.aquashelf.domain.reminders.NotificationAccess

object Notifications {
    const val CHANNEL_ID = "water_reminders"

    /** Single ID: a new reminder replaces the previous one; no backlog piles up. */
    const val REMINDER_NOTIFICATION_ID = 2001
    const val EXTRA_OPEN_TODAY = "com.egrmeister.aquashelf.OPEN_TODAY"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun access(context: Context, alreadyPrompted: Boolean): NotificationAccess {
        val compat = NotificationManagerCompat.from(context)
        val runtimeRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val granted = !runtimeRequired || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val channel = compat.getNotificationChannel(CHANNEL_ID)
        // Channel missing → it is recreated before posting; a channel the user blocked stays blocked.
        val channelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        return NotificationAccess(
            runtimePermissionRequired = runtimeRequired,
            permissionGranted = granted,
            appNotificationsEnabled = compat.areNotificationsEnabled(),
            channelEnabled = channelEnabled,
            alreadyPrompted = alreadyPrompted,
        )
    }

    @SuppressLint("MissingPermission") // Permission is checked explicitly right before notify().
    fun postReminder(context: Context): Boolean {
        createChannel(context)
        if (!access(context, alreadyPrompted = true).canDeliver) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TODAY, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
        return true
    }

    fun cancelReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(REMINDER_NOTIFICATION_ID)
    }
}

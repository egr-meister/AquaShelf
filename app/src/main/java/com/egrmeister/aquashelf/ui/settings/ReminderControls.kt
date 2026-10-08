package com.egrmeister.aquashelf.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.egrmeister.aquashelf.domain.entries.Fmt
import com.egrmeister.aquashelf.domain.reminders.ReminderSettings
import com.egrmeister.aquashelf.domain.reminders.ReminderStatus
import com.egrmeister.aquashelf.reminders.Notifications
import com.egrmeister.aquashelf.ui.components.FieldButton
import com.egrmeister.aquashelf.ui.components.TimePickerDialog
import com.egrmeister.aquashelf.ui.theme.LocalUse24Hour
import com.egrmeister.aquashelf.ui.theme.ShelfColors

/**
 * Reminder configuration with the notification-permission flow:
 * - POST_NOTIFICATIONS is requested only after the user turns reminders on (Android 13+),
 * - the system prompt is shown at most once,
 * - if delivery is blocked, logging keeps working and an optional settings link is offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderControls(
    settings: ReminderSettings,
    alreadyPrompted: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onWindowChange: (Int, Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onPrompted: () -> Unit,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val use24h = LocalUse24Hour.current
    var refresh by remember { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val access = remember(refresh, alreadyPrompted, settings.enabled) { Notifications.access(context, alreadyPrompted) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        onPermissionResult()
    }

    fun toggle(on: Boolean) {
        onEnabledChange(on)
        if (on && access.shouldRequestPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onPrompted()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = ::toggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional local notifications inviting you to record water. Off by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShelfColors.SlateSoft,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = null)
        }

        if (settings.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldButton(
                    label = "Start",
                    value = Fmt.minuteOfDay(settings.startMinute, use24h),
                    accessibilityLabel = "Start time ${Fmt.minuteOfDay(settings.startMinute, use24h)}, change",
                    modifier = Modifier.weight(1f),
                ) { picking = "start" }
                FieldButton(
                    label = "End",
                    value = Fmt.minuteOfDay(settings.endMinute, use24h),
                    accessibilityLabel = "End time ${Fmt.minuteOfDay(settings.endMinute, use24h)}, change",
                    modifier = Modifier.weight(1f),
                ) { picking = "end" }
            }
            Text("Interval", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderSettings.INTERVALS.forEach { minutes ->
                    FilterChip(
                        selected = settings.intervalMinutes == minutes,
                        onClick = { onIntervalChange(minutes) },
                        label = { Text("$minutes min") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        when (access.status(settings.enabled)) {
            ReminderStatus.OFF -> Unit
            ReminderStatus.ACTIVE -> Text(
                "Reminders are inexact: Android may delay them to save battery. " +
                    "Force-stopping AquaShelf stops reminders until you open the app again.",
                style = MaterialTheme.typography.bodySmall,
                color = ShelfColors.SlateSoft,
            )
            ReminderStatus.NEEDS_PERMISSION -> Text(
                "AquaShelf will ask once for permission to show notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = ShelfColors.SlateSoft,
            )
            ReminderStatus.BLOCKED -> {
                Text(
                    "Notifications are blocked for AquaShelf, so reminders can't be shown. " +
                        "Logging water still works normally.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { openNotificationSettings(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open notification settings")
                }
            }
        }
    }

    when (picking) {
        "start" -> TimePickerDialog("Start time", settings.startMinute, use24h, onDismiss = { picking = null }) {
            onWindowChange(it, settings.endMinute)
            picking = null
        }
        "end" -> TimePickerDialog("End time", settings.endMinute, use24h, onDismiss = { picking = null }) {
            onWindowChange(settings.startMinute, it)
            picking = null
        }
    }
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

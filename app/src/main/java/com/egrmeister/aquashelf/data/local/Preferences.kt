package com.egrmeister.aquashelf.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egrmeister.aquashelf.domain.reminders.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reminder configuration, animation preference and lightweight UI state (DataStore). */
data class AppPrefs(
    val reminders: ReminderSettings = ReminderSettings(),
    val notificationPromptShown: Boolean = false,
    val reducedAnimation: Boolean = false,
    val shelfAsList: Boolean = false,
    val starterPresetsSeeded: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aquashelf_prefs")

class PreferencesStore(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val START = intPreferencesKey("reminder_start_minute")
        val END = intPreferencesKey("reminder_end_minute")
        val INTERVAL = intPreferencesKey("reminder_interval_minutes")
        val PROMPTED = booleanPreferencesKey("notification_prompt_shown")
        val REDUCED_ANIMATION = booleanPreferencesKey("reduced_animation")
        val SHELF_AS_LIST = booleanPreferencesKey("shelf_as_list")
        val SEEDED = booleanPreferencesKey("starter_presets_seeded")
    }

    val prefs: Flow<AppPrefs> = store.data.map { p ->
        val defaults = ReminderSettings()
        val interval = p[Keys.INTERVAL]?.takeIf { it in ReminderSettings.INTERVALS } ?: defaults.intervalMinutes
        AppPrefs(
            reminders = ReminderSettings(
                enabled = p[Keys.REMINDERS] ?: false,
                startMinute = p[Keys.START] ?: defaults.startMinute,
                endMinute = p[Keys.END] ?: defaults.endMinute,
                intervalMinutes = interval,
            ),
            notificationPromptShown = p[Keys.PROMPTED] ?: false,
            reducedAnimation = p[Keys.REDUCED_ANIMATION] ?: false,
            shelfAsList = p[Keys.SHELF_AS_LIST] ?: false,
            starterPresetsSeeded = p[Keys.SEEDED] ?: false,
        )
    }

    suspend fun current(): AppPrefs = prefs.first()

    suspend fun setRemindersEnabled(enabled: Boolean) {
        store.edit { it[Keys.REMINDERS] = enabled }
    }

    /** Persists a window only when it is valid (end later than start, same day). */
    suspend fun setReminderWindow(startMinute: Int, endMinute: Int): Boolean {
        if (ReminderSettings.windowError(startMinute, endMinute) != null) return false
        store.edit {
            it[Keys.START] = startMinute
            it[Keys.END] = endMinute
        }
        return true
    }

    suspend fun setReminderInterval(minutes: Int): Boolean {
        if (minutes !in ReminderSettings.INTERVALS) return false
        store.edit { it[Keys.INTERVAL] = minutes }
        return true
    }

    suspend fun markNotificationPromptShown() {
        store.edit { it[Keys.PROMPTED] = true }
    }

    suspend fun setReducedAnimation(enabled: Boolean) {
        store.edit { it[Keys.REDUCED_ANIMATION] = enabled }
    }

    suspend fun setShelfAsList(enabled: Boolean) {
        store.edit { it[Keys.SHELF_AS_LIST] = enabled }
    }

    suspend fun markStarterPresetsSeeded() {
        store.edit { it[Keys.SEEDED] = true }
    }

    /** Removes every preference; [keepSeeded] re-marks starter presets as seeded after a reset. */
    suspend fun clear(keepSeeded: Boolean) {
        store.edit {
            it.clear()
            if (keepSeeded) it[Keys.SEEDED] = true
        }
    }
}

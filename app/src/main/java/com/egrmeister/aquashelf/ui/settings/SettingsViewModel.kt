package com.egrmeister.aquashelf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.aquashelf.AppContainer
import com.egrmeister.aquashelf.data.local.AppPrefs
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.presets.Preset
import com.egrmeister.aquashelf.domain.presets.PresetResult
import com.egrmeister.aquashelf.domain.reminders.ReminderSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loaded: Boolean = false,
    val prefs: AppPrefs = AppPrefs(),
    val presets: List<Preset> = emptyList(),
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(container.preferences.prefs, container.presets.observe()) { p, list ->
        SettingsUiState(loaded = true, prefs = p, presets = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    // ---- Presets ----

    /** Returns the validation messages via [onResult] (empty list = saved). */
    fun savePreset(id: Long?, name: String, type: ContainerType, amountText: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val result = if (id == null) {
                container.presets.service.add(name, type, amountText)
            } else {
                container.presets.service.update(id, name, type, amountText)
            }
            when (result) {
                is PresetResult.Saved -> onResult(emptyList())
                is PresetResult.Rejected -> onResult(result.errors.map { it.message })
            }
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            if (container.presets.service.delete(id)) messageChannel.trySend("Preset deleted. Earlier entries are unchanged.")
        }
    }

    fun movePreset(id: Long, up: Boolean) {
        val key = "move-preset-$id"
        if (!container.actionGuard.tryAcquire(key)) return
        viewModelScope.launch {
            try {
                container.presets.service.move(id, up)
            } finally {
                container.actionGuard.release(key)
            }
        }
    }

    // ---- Reminders ----

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setRemindersEnabled(enabled)
            if (enabled) container.reminderScheduler.reschedule() else container.reminderScheduler.cancelAll()
        }
    }

    fun setWindow(startMinute: Int, endMinute: Int) {
        val error = ReminderSettings.windowError(startMinute, endMinute)
        if (error != null) {
            messageChannel.trySend(error)
            return
        }
        viewModelScope.launch {
            container.preferences.setReminderWindow(startMinute, endMinute)
            container.reminderScheduler.reschedule()
        }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch {
            container.preferences.setReminderInterval(minutes)
            container.reminderScheduler.reschedule()
        }
    }

    fun markPrompted() {
        viewModelScope.launch { container.preferences.markNotificationPromptShown() }
    }

    fun onPermissionResult() {
        viewModelScope.launch { container.reminderScheduler.reschedule() }
    }

    // ---- Display ----

    fun setReducedAnimation(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setReducedAnimation(enabled) }
    }

    // ---- Reset ----

    fun clearAllData(onDone: () -> Unit) {
        val key = "clear-all"
        if (!container.actionGuard.tryAcquire(key)) return
        viewModelScope.launch {
            try {
                container.clearAllData()
                onDone()
            } finally {
                container.actionGuard.release(key)
            }
        }
    }
}

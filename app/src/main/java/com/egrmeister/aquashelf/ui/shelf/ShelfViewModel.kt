package com.egrmeister.aquashelf.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.aquashelf.AppContainer
import com.egrmeister.aquashelf.domain.entries.DayTotals
import com.egrmeister.aquashelf.domain.entries.Fmt
import com.egrmeister.aquashelf.domain.entries.ShelfOrder
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import com.egrmeister.aquashelf.domain.presets.Preset
import com.egrmeister.aquashelf.reminders.Notifications
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ShelfUiState(
    val loaded: Boolean = false,
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    /** Chronological shelf order (left to right, shelf by shelf). */
    val entries: List<WaterEntry> = emptyList(),
    val totals: DayTotals = DayTotals.EMPTY,
    /** Most recently LOGGED entry on this day — the one Undo Last removes. */
    val latestLogged: WaterEntry? = null,
    val presets: List<Preset> = emptyList(),
    val shelfAsList: Boolean = false,
    val reducedAnimation: Boolean = false,
) {
    val isToday: Boolean get() = date == today
    val isFuture: Boolean get() = date.isAfter(today)
    val canUndo: Boolean get() = latestLogged != null && !isFuture
}

private data class DayData(val date: LocalDate, val entries: List<WaterEntry>)

/**
 * Shelf for one day. [fixedDate] == null means "Today", which follows midnight and foreground
 * refreshes; a fixed date is a historical (or, if the clock moved back, future read-only) shelf.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModel(private val container: AppContainer, private val fixedDate: LocalDate?) : ViewModel() {

    private val dateFlow: Flow<LocalDate> = fixedDate?.let { flowOf(it) } ?: container.todayProvider.today

    private val dayFlow: Flow<DayData> = dateFlow.flatMapLatest { date ->
        container.entries.observeDay(date).map { DayData(date, it) }
    }

    val state: StateFlow<ShelfUiState> = combine(
        dayFlow,
        container.presets.observe(),
        container.preferences.prefs,
        container.todayProvider.today,
    ) { day, presets, prefs, today ->
        val arranged = ShelfOrder.arrange(day.entries)
        ShelfUiState(
            loaded = true,
            date = day.date,
            today = today,
            entries = arranged,
            totals = DayTotals.of(arranged),
            latestLogged = ShelfOrder.latestLogged(arranged),
            presets = presets,
            shelfAsList = prefs.shelfAsList,
            reducedAnimation = prefs.reducedAnimation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfUiState())

    private val _justAdded = MutableStateFlow<Long?>(null)
    /** ID of the entry just placed on the shelf (drives the brief placement animation). */
    val justAdded: StateFlow<Long?> = _justAdded.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val guard get() = container.actionGuard

    /** Quick logging from a preset: one entry at the current local date and time. */
    fun logPreset(preset: Preset) {
        val key = "log-preset-${preset.id}"
        if (!guard.tryAcquire(key)) return
        viewModelScope.launch {
            try {
                val entry = container.entries.service.logNow(preset.amountMl, preset.type, preset.name)
                container.todayProvider.refresh()
                Notifications.cancelReminder(container.context)
                markJustAdded(entry.id)
                messageChannel.trySend("Added ${Fmt.entryShort(entry)}")
            } finally {
                guard.release(key)
            }
        }
    }

    /** Undo Last: removes exactly the entry shown on the button, or nothing. */
    fun undoLast() {
        val s = state.value
        val target = s.latestLogged ?: return
        if (s.isFuture) return
        val key = "undo-${s.date}"
        if (!guard.tryAcquire(key)) return
        viewModelScope.launch {
            try {
                val removed = container.entries.service.undoLast(s.date, expectedId = target.id)
                if (removed != null) messageChannel.trySend("Removed ${Fmt.entryShort(removed)}")
            } finally {
                guard.release(key)
            }
        }
    }

    fun delete(id: Long) {
        val key = "delete-$id"
        if (!guard.tryAcquire(key)) return
        viewModelScope.launch {
            try {
                val removed = container.entries.service.delete(id)
                if (removed != null) messageChannel.trySend("Deleted ${Fmt.entryShort(removed)}")
            } finally {
                guard.release(key)
            }
        }
    }

    fun setShelfAsList(asList: Boolean) {
        viewModelScope.launch { container.preferences.setShelfAsList(asList) }
    }

    private fun markJustAdded(id: Long) {
        _justAdded.value = id
        viewModelScope.launch {
            delay(1_500)
            if (_justAdded.value == id) _justAdded.value = null
        }
    }
}

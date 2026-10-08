package com.egrmeister.aquashelf.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.egrmeister.aquashelf.AppContainer
import com.egrmeister.aquashelf.domain.entries.AmountResult
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.EditResult
import com.egrmeister.aquashelf.domain.entries.EntryTimeResult
import com.egrmeister.aquashelf.domain.entries.EntryValidator
import com.egrmeister.aquashelf.domain.entries.Fmt
import com.egrmeister.aquashelf.reminders.Notifications
import com.egrmeister.aquashelf.ui.components.ConfirmDialog
import com.egrmeister.aquashelf.ui.components.FieldButton
import com.egrmeister.aquashelf.ui.components.PastDatePickerDialog
import com.egrmeister.aquashelf.ui.components.TimePickerDialog
import com.egrmeister.aquashelf.ui.shelf.ContainerIcon
import com.egrmeister.aquashelf.ui.theme.LocalUse24Hour
import com.egrmeister.aquashelf.ui.theme.ShelfColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Editable draft. [minuteOfDay] is null until a time is chosen (required for historical days). */
data class EntryForm(
    val amountText: String,
    val type: ContainerType,
    val date: LocalDate,
    val minuteOfDay: Int?,
    /** Seconds of an existing entry, kept unless its date or time is changed. */
    val keepSecond: Int = 0,
)

/** Preset the new entry was started from; its name is snapshotted only if amount and type are unchanged. */
data class PresetSource(val name: String, val type: ContainerType, val amountMl: Int)

data class EditorState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val missing: Boolean = false,
    val original: EntryForm? = null,
    val form: EntryForm? = null,
    val presetSource: PresetSource? = null,
    val presetSnapshot: String? = null,
    val error: String? = null,
    val today: LocalDate = LocalDate.MIN,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != null && form != original
}

class EntryEditorViewModel(
    private val container: AppContainer,
    private val handle: SavedStateHandle,
    private val entryId: Long?,
    private val initialDate: LocalDate?,
    private val presetId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Zone of the entry being edited; edited local times are interpreted in it. */
    private var cachedZone: ZoneId? = null

    init {
        val today = container.time.today()
        val restored = restore()
        if (restored != null) {
            _state.value = restored.copy(today = today)
        } else {
            viewModelScope.launch { load(today) }
        }
    }

    private suspend fun load(today: LocalDate) {
        val now = container.time.now().atZone(container.time.zone())
        if (entryId != null) {
            val existing = container.entries.entry(entryId)
            if (existing == null) {
                _state.value = EditorState(loading = false, isEdit = true, missing = true, today = today)
                return
            }
            val form = EntryForm(
                amountText = existing.amountMl.toString(),
                type = existing.type,
                date = existing.localDate,
                minuteOfDay = existing.localTime.hour * 60 + existing.localTime.minute,
                keepSecond = existing.localTime.second,
            )
            publish(EditorState(loading = false, isEdit = true, original = form, form = form, presetSnapshot = existing.presetNameSnapshot, today = today))
        } else {
            val date = initialDate?.takeIf { !it.isAfter(today) } ?: today
            val preset = presetId?.let { container.presets.preset(it) }
            val form = EntryForm(
                amountText = preset?.amountMl?.toString() ?: "",
                type = preset?.type ?: ContainerType.GLASS,
                date = date,
                // Today: current time. A historical day: the user must choose a valid time.
                minuteOfDay = if (date == today) now.hour * 60 + now.minute else null,
            )
            publish(
                EditorState(
                    loading = false,
                    isEdit = false,
                    original = form,
                    form = form,
                    presetSource = preset?.let { PresetSource(it.name, it.type, it.amountMl) },
                    today = today,
                ),
            )
        }
    }

    fun update(transform: (EntryForm) -> EntryForm) {
        _state.update { s -> s.copy(form = s.form?.let(transform), error = null) }
        persist(_state.value)
    }

    fun save() {
        val s = _state.value
        val form = s.form ?: return
        if (s.saving) return
        val key = "save-entry-${entryId ?: "new"}"
        if (!container.actionGuard.tryAcquire(key)) return
        val amount = when (val r = EntryValidator.parseAmount(form.amountText)) {
            is AmountResult.Invalid -> return fail(key, r.message)
            is AmountResult.Valid -> r.amountMl
        }
        val minute = form.minuteOfDay ?: return fail(key, "Choose a time for this entry.")
        val original = s.original
        val unchangedMoment = original != null && s.isEdit && original.date == form.date && original.minuteOfDay == form.minuteOfDay
        val time = LocalTime.of(minute / 60, minute % 60, if (unchangedMoment) form.keepSecond else 0)
        val zone = if (s.isEdit) entryZone() ?: container.time.zone() else container.time.zone()
        val at = when (val r = EntryValidator.resolve(form.date, time, zone, container.time.now())) {
            is EntryTimeResult.Invalid -> return fail(key, r.message)
            is EntryTimeResult.Valid -> r.zoned
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (entryId != null) {
                    when (container.entries.service.edit(entryId, amount, form.type, at)) {
                        is EditResult.Updated -> Unit
                        EditResult.NotFound -> {
                            _state.update { it.copy(saving = false, error = "This entry no longer exists.") }
                            return@launch
                        }
                    }
                } else {
                    val source = s.presetSource
                    val snapshot = source?.takeIf { it.type == form.type && it.amountMl == amount }?.name
                    container.entries.service.addManual(amount, form.type, snapshot, at)
                }
                container.todayProvider.refresh()
                if (at.toLocalDate() == container.time.today()) Notifications.cancelReminder(container.context)
                clearDraft()
                _state.update { it.copy(saving = false, done = true) }
            } finally {
                container.actionGuard.release(key)
            }
        }
    }

    fun delete() {
        val id = entryId ?: return
        val key = "delete-$id"
        if (!container.actionGuard.tryAcquire(key)) return
        viewModelScope.launch {
            try {
                container.entries.service.delete(id)
                clearDraft()
                _state.update { it.copy(done = true) }
            } finally {
                container.actionGuard.release(key)
            }
        }
    }

    /** Called after the user confirmed discarding. */
    fun discard() {
        clearDraft()
    }

    private fun fail(key: String, message: String) {
        _state.update { it.copy(error = message) }
        container.actionGuard.release(key)
    }

    private fun entryZone(): ZoneId? = cachedZone

    private fun publish(state: EditorState) {
        _state.value = state
        if (state.isEdit && entryId != null) {
            viewModelScope.launch {
                cachedZone = container.entries.entry(entryId)?.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            }
        }
        persist(state)
    }

    // --- Draft persistence (rotation is covered by the ViewModel; process death by SavedStateHandle) ---

    private fun persist(s: EditorState) {
        val form = s.form ?: return
        val original = s.original ?: return
        handle[K_LOADED] = true
        handle[K_IS_EDIT] = s.isEdit
        writeForm("f_", form)
        writeForm("o_", original)
        handle[K_SNAPSHOT] = s.presetSnapshot
        handle[K_SRC_NAME] = s.presetSource?.name
        handle[K_SRC_TYPE] = s.presetSource?.type?.name
        handle[K_SRC_AMOUNT] = s.presetSource?.amountMl ?: -1
    }

    private fun writeForm(prefix: String, f: EntryForm) {
        handle[prefix + "amount"] = f.amountText
        handle[prefix + "type"] = f.type.name
        handle[prefix + "date"] = f.date.toString()
        handle[prefix + "minute"] = f.minuteOfDay ?: -1
        handle[prefix + "second"] = f.keepSecond
    }

    private fun readForm(prefix: String): EntryForm? {
        val amount = handle.get<String>(prefix + "amount") ?: return null
        val type = handle.get<String>(prefix + "type")?.let { ContainerType.fromStorage(it) } ?: return null
        val date = handle.get<String>(prefix + "date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val minute = handle.get<Int>(prefix + "minute")?.takeIf { it >= 0 }
        val second = handle.get<Int>(prefix + "second") ?: 0
        return EntryForm(amount, type, date, minute, second)
    }

    private fun restore(): EditorState? {
        if (handle.get<Boolean>(K_LOADED) != true) return null
        val form = readForm("f_") ?: return null
        val original = readForm("o_") ?: return null
        val isEdit = handle.get<Boolean>(K_IS_EDIT) ?: false
        val srcName = handle.get<String>(K_SRC_NAME)
        val srcType = handle.get<String>(K_SRC_TYPE)?.let { ContainerType.fromStorage(it) }
        val srcAmount = handle.get<Int>(K_SRC_AMOUNT) ?: -1
        val source = if (srcName != null && srcType != null && srcAmount > 0) PresetSource(srcName, srcType, srcAmount) else null
        if (isEdit && entryId != null) {
            viewModelScope.launch {
                cachedZone = container.entries.entry(entryId)?.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            }
        }
        return EditorState(
            loading = false,
            isEdit = isEdit,
            original = original,
            form = form,
            presetSource = source,
            presetSnapshot = handle.get<String>(K_SNAPSHOT),
        )
    }

    private fun clearDraft() {
        handle.keys().toList().forEach { handle.remove<Any>(it) }
    }

    private companion object {
        const val K_LOADED = "draft_loaded"
        const val K_IS_EDIT = "draft_is_edit"
        const val K_SNAPSHOT = "draft_snapshot"
        const val K_SRC_NAME = "draft_src_name"
        const val K_SRC_TYPE = "draft_src_type"
        const val K_SRC_AMOUNT = "draft_src_amount"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(viewModel: EntryEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val use24h = LocalUse24Hour.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.done) { if (state.done) onClose() }

    val requestClose: () -> Unit = {
        if (state.dirty) confirmDiscard = true else onClose()
    }
    // Unsaved changes ask before discarding; works with predictive Back.
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (state.isEdit) "Edit entry" else "Add water") },
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.missing) {
                Text("This entry no longer exists.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(12.dp))
                Button(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
                return@Column
            }
            val form = state.form ?: return@Column
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = form.amountText,
                    onValueChange = { v -> viewModel.update { it.copy(amountText = v.filter { c -> c.isDigit() }.take(4)) } },
                    label = { Text("Amount (mL)") },
                    supportingText = { Text("Whole milliliters, 1–5,000. This is an input limit, not guidance.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Container", style = MaterialTheme.typography.labelLarge, color = ShelfColors.Slate)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContainerType.entries.forEach { type ->
                            FilterChip(
                                selected = form.type == type,
                                onClick = { viewModel.update { it.copy(type = type) } },
                                label = { Text(type.label) },
                                leadingIcon = { ContainerIcon(type, Modifier.clearAndSetSemantics { }, height = 22.dp) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    if (state.isEdit && state.presetSnapshot != null) {
                        Text(
                            if (form.type == ContainerType.CUSTOM) {
                                "Saving as Custom removes the preset name \"${state.presetSnapshot}\"."
                            } else {
                                "Logged with preset \"${state.presetSnapshot}\"."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ShelfColors.SlateSoft,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldButton(
                        label = "Date",
                        value = Fmt.shortDay(form.date),
                        accessibilityLabel = "Date ${Fmt.longDay(form.date)}, change",
                        modifier = Modifier.weight(1f),
                    ) { picking = "date" }
                    val minute = form.minuteOfDay
                    FieldButton(
                        label = "Time",
                        value = if (minute != null) Fmt.minuteOfDay(minute, use24h) else "Choose",
                        accessibilityLabel = if (minute != null) "Time ${Fmt.minuteOfDay(minute, use24h)}, change" else "Time not chosen, choose a time",
                        modifier = Modifier.weight(1f),
                        isError = minute == null && state.error != null,
                    ) { picking = "time" }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = requestClose, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Cancel") }
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Save") }
                }
                if (state.isEdit) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Delete entry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    val form = state.form
    if (form != null) {
        when (picking) {
            "date" -> PastDatePickerDialog(form.date, state.today, onDismiss = { picking = null }) { d ->
                viewModel.update { it.copy(date = d) }
                picking = null
            }
            "time" -> TimePickerDialog(
                title = "Time",
                initialMinute = form.minuteOfDay ?: (12 * 60),
                is24Hour = use24h,
                onDismiss = { picking = null },
            ) { m ->
                viewModel.update { it.copy(minuteOfDay = m) }
                picking = null
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your unsaved changes will be lost.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discard()
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            text = "It will be removed from its shelf and the totals will be updated.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

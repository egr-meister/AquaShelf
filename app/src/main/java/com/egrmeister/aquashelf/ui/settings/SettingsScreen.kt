package com.egrmeister.aquashelf.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.Fmt
import com.egrmeister.aquashelf.domain.entries.Limits
import com.egrmeister.aquashelf.domain.presets.Preset
import com.egrmeister.aquashelf.ui.components.ConfirmDialog
import com.egrmeister.aquashelf.ui.shelf.ContainerIcon
import com.egrmeister.aquashelf.ui.theme.ShelfColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPrivacy: () -> Unit,
    onDataCleared: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Preset dialog: null = closed, -1 = new preset, otherwise the preset ID being edited.
    var editingPresetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletePresetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Container presets")
                Text(
                    "Up to ${Limits.MAX_PRESETS} presets. Changing a preset only affects future entries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShelfColors.SlateSoft,
                )
                if (state.presets.isEmpty()) {
                    Text(
                        "No presets. Add Custom on the shelf is always available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ShelfColors.SlateSoft,
                    )
                }
                state.presets.forEachIndexed { index, preset ->
                    PresetRow(
                        preset = preset,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.presets.lastIndex,
                        onEdit = { editingPresetId = preset.id },
                        onMoveUp = { viewModel.movePreset(preset.id, up = true) },
                        onMoveDown = { viewModel.movePreset(preset.id, up = false) },
                        onDelete = { deletePresetId = preset.id },
                    )
                    HorizontalDivider(color = ShelfColors.CabinetEdge)
                }
                OutlinedButton(
                    onClick = { editingPresetId = NEW_PRESET },
                    enabled = state.presets.size < Limits.MAX_PRESETS,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (state.presets.size < Limits.MAX_PRESETS) "Add preset" else "Preset limit reached (8)") }

                Spacer(Modifier.heightIn(min = 8.dp))
                SectionTitle("Reminders")
                ReminderControls(
                    settings = state.prefs.reminders,
                    alreadyPrompted = state.prefs.notificationPromptShown,
                    onEnabledChange = viewModel::setRemindersEnabled,
                    onWindowChange = viewModel::setWindow,
                    onIntervalChange = viewModel::setInterval,
                    onPrompted = viewModel::markPrompted,
                    onPermissionResult = viewModel::onPermissionResult,
                )

                Spacer(Modifier.heightIn(min = 8.dp))
                SectionTitle("Display")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = state.prefs.reducedAnimation,
                            role = Role.Switch,
                            onValueChange = viewModel::setReducedAnimation,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Reduce animation", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Turns off the placement animation when an entry is added.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ShelfColors.SlateSoft,
                        )
                    }
                    Switch(checked = state.prefs.reducedAnimation, onCheckedChange = null)
                }
                Text("Units: milliliters (mL).", style = MaterialTheme.typography.bodySmall, color = ShelfColors.SlateSoft)

                Spacer(Modifier.heightIn(min = 8.dp))
                SectionTitle("Privacy & data")
                TextButton(onClick = onOpenPrivacy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Privacy information") }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Clear all local data", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.heightIn(min = 24.dp))
            }
        }
    }

    editingPresetId?.let { id ->
        val existing = state.presets.firstOrNull { it.id == id }
        PresetDialog(
            existing = existing,
            onDismiss = { editingPresetId = null },
            onSave = { name, type, amount, showErrors ->
                viewModel.savePreset(existing?.id, name, type, amount) { errors ->
                    if (errors.isEmpty()) editingPresetId = null else showErrors(errors)
                }
            },
        )
    }
    deletePresetId?.let { id ->
        val preset = state.presets.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "Delete preset?",
            text = "\"${preset?.name ?: "This preset"}\" will be removed. Entries already logged with it stay on their shelves.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                deletePresetId = null
                viewModel.deletePreset(id)
            },
            onDismiss = { deletePresetId = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all local data?",
            text = "This permanently removes every water record and preference on this device, cancels reminders " +
                "and restores the three starter presets. This can't be undone.",
            confirmLabel = "Clear all data",
            destructive = true,
            onConfirm = {
                confirmClear = false
                viewModel.clearAllData(onDataCleared)
            },
            onDismiss = { confirmClear = false },
        )
    }
}

private const val NEW_PRESET = -1L

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = ShelfColors.Slate,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun PresetRow(
    preset: Preset,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = "Edit preset", onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContainerIcon(preset.type, Modifier.clearAndSetSemantics { }, height = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(preset.name, style = MaterialTheme.typography.titleSmall, color = ShelfColors.Slate)
                Text(
                    "${preset.type.label} · ${Fmt.ml(preset.amountMl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShelfColors.SlateSoft,
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${preset.name} up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${preset.name} down")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${preset.name}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDialog(
    existing: Preset?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: ContainerType, amount: String, showErrors: (List<String>) -> Unit) -> Unit,
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var typeName by rememberSaveable(existing?.id) { mutableStateOf((existing?.type ?: ContainerType.GLASS).name) }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.amountMl?.toString() ?: "") }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    val type = ContainerType.fromStorage(typeName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New preset" else "Edit preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); errors = emptyList() },
                    label = { Text("Name") },
                    supportingText = { Text("${name.trim().length}/${Limits.PRESET_NAME_MAX}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ContainerType.GLASS, ContainerType.BOTTLE).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { typeName = t.name },
                            label = { Text(t.label) },
                            leadingIcon = { ContainerIcon(t, Modifier.clearAndSetSemantics { }, height = 22.dp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() }.take(4); errors = emptyList() },
                    label = { Text("Amount (mL)") },
                    supportingText = { Text("1–5,000 mL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (existing != null) {
                    Text(
                        "Entries already logged keep their original name, type and amount.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ShelfColors.SlateSoft,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, type, amount) { errors = it } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.egrmeister.aquashelf.ui.shelf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.Fmt
import com.egrmeister.aquashelf.domain.entries.ShelfOrder
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import com.egrmeister.aquashelf.domain.presets.Preset
import com.egrmeister.aquashelf.ui.components.ConfirmDialog
import com.egrmeister.aquashelf.ui.theme.LocalReducedAnimation
import com.egrmeister.aquashelf.ui.theme.LocalUse24Hour
import com.egrmeister.aquashelf.ui.theme.ShelfColors
import java.time.LocalDate

const val EMPTY_SHELF_TEXT = "Your shelf is empty. Add a glass or bottle."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    historical: Boolean,
    onBack: () -> Unit,
    onAddCustom: (LocalDate) -> Unit,
    onAddFromPreset: (LocalDate, Long) -> Unit,
    onEditEntry: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val justAdded by viewModel.justAdded.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val onPreset: (Preset) -> Unit = { preset ->
        // Never silently log against today from a historical shelf: open the form with that date.
        if (state.isToday) viewModel.logPreset(preset) else onAddFromPreset(state.date, preset.id)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (historical) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to week")
                        }
                    }
                },
                title = {
                    if (state.loaded) {
                        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                            Text(
                                Fmt.dayTitle(state.date, state.today),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                Fmt.longDay(state.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = ShelfColors.SlateSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (state.loaded && state.entries.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.setShelfAsList(!state.shelfAsList) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(if (state.shelfAsList) "Shelf view" else "List view") }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.5f).fillMaxSize()) {
                        Summary(state)
                        Spacer(Modifier.height(8.dp))
                        Cabinet(state, justAdded, Modifier.weight(1f).fillMaxWidth(), onSelect = { selectedId = it })
                    }
                    Column(
                        Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Controls(state, wrap = true, onPreset = onPreset, onAddCustom = { onAddCustom(state.date) }, onUndo = viewModel::undoLast)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Summary(state)
                    Spacer(Modifier.height(8.dp))
                    Cabinet(
                        state,
                        justAdded,
                        Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp),
                        onSelect = { selectedId = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    Controls(state, wrap = false, onPreset = onPreset, onAddCustom = { onAddCustom(state.date) }, onUndo = viewModel::undoLast)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    val selected = selectedId?.let { id -> state.entries.firstOrNull { it.id == id } }
    if (selectedId != null && state.loaded && selected == null) {
        // The entry moved to another day or was removed — close its details.
        LaunchedEffect(selectedId) { selectedId = null }
    }
    if (selected != null) {
        EntryDetailsDialog(
            entry = selected,
            readOnly = state.isFuture,
            onDismiss = { selectedId = null },
            onEdit = { selectedId = null; onEditEntry(selected.id) },
            onDelete = { confirmDeleteId = selected.id },
        )
    }
    confirmDeleteId?.let { id ->
        ConfirmDialog(
            title = "Delete this entry?",
            text = "It will be removed from the shelf and the totals will be updated.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDeleteId = null
                selectedId = null
                viewModel.delete(id)
            },
            onDismiss = { confirmDeleteId = null },
        )
    }
}

@Composable
private fun Summary(state: ShelfUiState) {
    val totals = state.totals
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Text(totals.volumeLabel, style = MaterialTheme.typography.headlineSmall, color = ShelfColors.Slate)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (totals.entries == 0) "No entries" else totals.breakdown,
                style = MaterialTheme.typography.bodyLarge,
                color = ShelfColors.SlateSoft,
            )
            if (totals.entries > 0) {
                Text("·", color = ShelfColors.SlateFaint)
                Text(totals.entriesLabel, style = MaterialTheme.typography.bodyLarge, color = ShelfColors.SlateSoft)
            }
        }
    }
}

/** The cabinet: horizontal wooden shelves holding one object per saved entry, lazily laid out. */
@Composable
private fun Cabinet(state: ShelfUiState, justAdded: Long?, modifier: Modifier, onSelect: (Long) -> Unit) {
    Surface(
        modifier = modifier,
        color = ShelfColors.Cabinet,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ShelfColors.CabinetEdge),
        shadowElevation = 1.dp,
    ) {
        when {
            state.entries.isEmpty() -> EmptyCabinet(state.isFuture)
            state.shelfAsList -> EntryList(state.entries, onSelect)
            else -> Shelves(state.entries, justAdded, onSelect)
        }
    }
}

@Composable
private fun EmptyCabinet(isFuture: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Plank()
        Text(
            if (isFuture) "No records. Future dates are read-only." else EMPTY_SHELF_TEXT,
            style = MaterialTheme.typography.bodyLarge,
            color = ShelfColors.SlateSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Plank()
    }
}

@Composable
private fun Shelves(entries: List<WaterEntry>, justAdded: Long?, onSelect: (Long) -> Unit) {
    val reduced = LocalReducedAnimation.current
    val use24h = LocalUse24Hour.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val perShelf = ShelfOrder.perShelf(maxWidth.value - 24f)
        val rows = remember(entries, perShelf) { ShelfOrder.shelves(entries, perShelf) }
        val listState = rememberLazyListState()
        LaunchedEffect(justAdded, rows.size) {
            val row = rows.indexOfFirst { r -> r.any { it.id == justAdded } }
            if (justAdded != null && row >= 0) listState.animateScrollToItem(row)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> "shelf-" + row.first().id }) { _, row ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        row.forEach { entry ->
                            ShelfObject(
                                entry = entry,
                                label = Fmt.entryLabel(entry, use24h),
                                animateIn = !reduced && entry.id == justAdded,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(entry.id) },
                            )
                        }
                        repeat(perShelf - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Plank()
                }
            }
        }
    }
}

@Composable
private fun ShelfObject(entry: WaterEntry, label: String, animateIn: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale = remember(entry.id) { Animatable(if (animateIn) 0.3f else 1f) }
    LaunchedEffect(entry.id, animateIn) {
        if (animateIn) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            scale.snapTo(1f)
        }
    }
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Show details", onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainerIcon(
            entry.type,
            Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clearAndSetSemantics { },
            height = 54.dp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            Fmt.ml(entry.amountMl),
            style = MaterialTheme.typography.labelMedium,
            color = ShelfColors.Slate,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Plank() {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Brush.verticalGradient(listOf(ShelfColors.Wood, ShelfColors.WoodDark)),
                    RoundedCornerShape(3.dp),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(4.dp)
                .background(Brush.verticalGradient(listOf(ShelfColors.WoodShadow, Color.Transparent))),
        )
    }
}

/** Accessible list equivalent of the shelf, in the same chronological order. */
@Composable
private fun EntryList(entries: List<WaterEntry>, onSelect: (Long) -> Unit) {
    val use24h = LocalUse24Hour.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(entries, key = { it.id }) { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = "Show details") { onSelect(entry.id) }
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContainerIcon(entry.type, Modifier.clearAndSetSemantics { }, height = 32.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${entry.type.label} · ${Fmt.ml(entry.amountMl)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = ShelfColors.Slate,
                    )
                    Text(
                        listOfNotNull(Fmt.time(entry.localTime, use24h), entry.presetNameSnapshot).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = ShelfColors.SlateSoft,
                    )
                }
            }
            HorizontalDivider(color = ShelfColors.CabinetEdge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    state: ShelfUiState,
    wrap: Boolean,
    onPreset: (Preset) -> Unit,
    onAddCustom: () -> Unit,
    onUndo: () -> Unit,
) {
    if (state.isFuture) {
        Text(
            "Future dates are read-only.",
            style = MaterialTheme.typography.bodyMedium,
            color = ShelfColors.SlateSoft,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.presets.isEmpty()) {
            Text(
                "No presets. Use Add Custom, or create presets in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = ShelfColors.SlateSoft,
            )
        } else if (wrap) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.presets.forEach { p -> PresetButton(p, state, onPreset) }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.presets, key = { it.id }) { p -> PresetButton(p, state, onPreset) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onAddCustom,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShelfColors.Slate),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Custom", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val latest = state.latestLogged
            OutlinedButton(
                onClick = onUndo,
                enabled = state.canUndo,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .semantics {
                        contentDescription = if (latest != null) {
                            "Undo last, removes ${Fmt.entryShort(latest)}"
                        } else {
                            "Undo last, nothing to undo"
                        }
                    },
            ) {
                Text(
                    if (latest != null) "Undo Last · ${Fmt.ml(latest.amountMl)}" else "Undo Last",
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PresetButton(preset: Preset, state: ShelfUiState, onPreset: (Preset) -> Unit) {
    val description = if (state.isToday) {
        "Log ${preset.name}, ${preset.type.label}, ${Fmt.ml(preset.amountMl)}"
    } else {
        "Add ${preset.name}, ${Fmt.ml(preset.amountMl)} to ${Fmt.longDay(state.date)}"
    }
    Surface(
        onClick = { onPreset(preset) },
        shape = RoundedCornerShape(14.dp),
        color = ShelfColors.Surface,
        border = BorderStroke(1.dp, ShelfColors.CabinetEdge),
        shadowElevation = 1.dp,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .widthIn(min = 96.dp)
            .semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContainerIcon(preset.type, Modifier.clearAndSetSemantics { }, height = 34.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.clearAndSetSemantics { }) {
                Text(preset.name, style = MaterialTheme.typography.labelLarge, color = ShelfColors.Slate, maxLines = 1)
                Text(Fmt.ml(preset.amountMl), style = MaterialTheme.typography.bodySmall, color = ShelfColors.SlateSoft)
            }
        }
    }
}

@Composable
private fun EntryDetailsDialog(
    entry: WaterEntry,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val use24h = LocalUse24Hour.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = ShelfColors.Surface, tonalElevation = 2.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContainerIcon(entry.type, Modifier.clearAndSetSemantics { }, height = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${entry.type.label} · ${Fmt.ml(entry.amountMl)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                DetailLine("Amount", Fmt.ml(entry.amountMl))
                DetailLine("Container", if (entry.type == ContainerType.CUSTOM) "Custom" else entry.type.label)
                entry.presetNameSnapshot?.let { DetailLine("Preset", it) }
                DetailLine("Date", Fmt.longDay(entry.localDate))
                DetailLine("Time", Fmt.time(entry.localTime, use24h))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (!readOnly) {
                        TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
                    if (!readOnly) {
                        Button(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ShelfColors.SlateSoft, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = ShelfColors.Slate)
    }
}

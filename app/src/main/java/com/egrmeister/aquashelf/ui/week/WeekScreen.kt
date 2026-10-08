package com.egrmeister.aquashelf.ui.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.egrmeister.aquashelf.AppContainer
import com.egrmeister.aquashelf.domain.entries.Fmt
import com.egrmeister.aquashelf.domain.history.DateRange
import com.egrmeister.aquashelf.domain.history.DaySummary
import com.egrmeister.aquashelf.domain.history.WeekHistory
import com.egrmeister.aquashelf.ui.shelf.ContainerIcon
import com.egrmeister.aquashelf.ui.theme.ShelfColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class WeekUiState(
    val loaded: Boolean = false,
    val page: Int = 0,
    val range: DateRange? = null,
    val days: List<DaySummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(private val container: AppContainer) : ViewModel() {
    private val page = MutableStateFlow(0)

    val state: StateFlow<WeekUiState> = combine(page, container.todayProvider.today) { p, today -> p to today }
        .flatMapLatest { (p, today) ->
            val range = WeekHistory.range(today, p)
            container.entries.observeRange(range).map { entries ->
                WeekUiState(loaded = true, page = p, range = range, days = WeekHistory.summarize(today, range, entries))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    fun earlier() {
        page.value += 1
    }

    fun later() {
        if (page.value > 0) page.value -= 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    viewModel: WeekViewModel,
    onOpenDay: (LocalDate) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Week") },
            )
        },
    ) { padding ->
        val range = state.range
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::earlier, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    Text("Earlier")
                }
                Text(
                    if (range != null) "${Fmt.monthDay(range.start)} – ${Fmt.monthDay(range.end)}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = ShelfColors.SlateSoft,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(
                    onClick = viewModel::later,
                    enabled = state.page > 0,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Later")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.days, key = { it.date.toString() }) { day ->
                    DayCard(day, onOpen = { onOpenDay(day.date) })
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: DaySummary, onOpen: () -> Unit) {
    val title = if (day.isToday) "Today · ${Fmt.weekday(day.date)}" else Fmt.weekday(day.date)
    val description = buildString {
        append(title).append(", ").append(Fmt.longDay(day.date)).append(". ")
        if (day.hasRecords) {
            append(day.totals.volumeLabel).append(", ").append(day.totals.breakdown).append(", ")
            append(day.totals.entriesLabel).append(".")
        } else {
            append("No records.")
        }
        if (day.isFuture) append(" Read-only.")
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = ShelfColors.Cabinet,
        border = BorderStroke(1.dp, ShelfColors.CabinetEdge),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Column(Modifier.padding(14.dp).clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = ShelfColors.Slate)
                    Text(Fmt.monthDay(day.date), style = MaterialTheme.typography.bodySmall, color = ShelfColors.SlateSoft)
                }
                if (day.hasRecords) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(day.totals.volumeLabel, style = MaterialTheme.typography.titleSmall, color = ShelfColors.Slate)
                        Text(day.totals.breakdown, style = MaterialTheme.typography.bodySmall, color = ShelfColors.SlateSoft)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (day.hasRecords) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.preview.forEach { entry -> ContainerIcon(entry.type, height = 30.dp) }
                    if (day.moreCount > 0) {
                        Text(
                            "+${day.moreCount} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = ShelfColors.SlateSoft,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }
                }
            } else {
                Text(
                    "No records",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ShelfColors.SlateFaint,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        Brush.verticalGradient(listOf(ShelfColors.Wood, ShelfColors.WoodDark)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.width(0.dp))
        }
    }
}

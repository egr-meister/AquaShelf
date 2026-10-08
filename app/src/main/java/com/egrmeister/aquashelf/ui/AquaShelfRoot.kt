package com.egrmeister.aquashelf.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.egrmeister.aquashelf.AppContainer
import com.egrmeister.aquashelf.data.local.AppPrefs
import com.egrmeister.aquashelf.ui.entry.EntryEditorScreen
import com.egrmeister.aquashelf.ui.entry.EntryEditorViewModel
import com.egrmeister.aquashelf.ui.settings.PrivacyScreen
import com.egrmeister.aquashelf.ui.settings.SettingsScreen
import com.egrmeister.aquashelf.ui.settings.SettingsViewModel
import com.egrmeister.aquashelf.ui.shelf.ShelfScreen
import com.egrmeister.aquashelf.ui.shelf.ShelfViewModel
import com.egrmeister.aquashelf.ui.theme.AquaShelfTheme
import com.egrmeister.aquashelf.ui.theme.LocalReducedAnimation
import com.egrmeister.aquashelf.ui.theme.LocalUse24Hour
import com.egrmeister.aquashelf.ui.theme.ShelfColors
import com.egrmeister.aquashelf.ui.week.WeekScreen
import com.egrmeister.aquashelf.ui.week.WeekViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

private object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DAY = "day/{date}"
    const val ENTRY = "entry?id={id}&date={date}&preset={preset}"

    fun day(date: LocalDate) = "day/$date"
    fun newEntry(date: LocalDate, presetId: Long? = null) = "entry?id=-1&date=$date&preset=${presetId ?: -1}"
    fun editEntry(id: Long) = "entry?id=$id&date=&preset=-1"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY(Routes.TODAY, "Today", Icons.Filled.Home),
    WEEK(Routes.WEEK, "Week", Icons.Filled.DateRange),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}

@Composable
fun AquaShelfRoot(container: AppContainer, openTodayRequests: StateFlow<Int>) {
    val prefs by container.preferences.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val use24h = DateFormat.is24HourFormat(LocalContext.current)

    AquaShelfTheme {
        CompositionLocalProvider(
            LocalReducedAnimation provides prefs.reducedAnimation,
            LocalUse24Hour provides use24h,
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AppNavHost(container, openTodayRequests)
            }
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer, openTodayRequests: StateFlow<Int>) {
    val nav = rememberNavController()
    val openToday by openTodayRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openToday) {
        if (openToday > 0) nav.goToToday()
    }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val bottomBar: @Composable () -> Unit = {
        AppNavigationBar(currentRoute) { tab -> nav.openTab(tab) }
    }

    NavHost(navController = nav, startDestination = Routes.TODAY) {
        composable(Routes.TODAY) {
            val vm: ShelfViewModel = viewModel(factory = viewModelFactory { initializer { ShelfViewModel(container, null) } })
            ShelfScreen(
                viewModel = vm,
                historical = false,
                onBack = {},
                onAddCustom = { nav.navigate(Routes.newEntry(it)) },
                onAddFromPreset = { date, presetId -> nav.navigate(Routes.newEntry(date, presetId)) },
                onEditEntry = { nav.navigate(Routes.editEntry(it)) },
                bottomBar = bottomBar,
            )
        }
        composable(Routes.DAY, arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
            val date = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: container.time.today()
            val vm: ShelfViewModel = viewModel(
                key = "day-$date",
                factory = viewModelFactory { initializer { ShelfViewModel(container, date) } },
            )
            ShelfScreen(
                viewModel = vm,
                historical = true,
                // Back from a historical shelf returns to Week.
                onBack = { if (!nav.popBackStack(Routes.WEEK, inclusive = false)) nav.openTab(Tab.WEEK) },
                onAddCustom = { nav.navigate(Routes.newEntry(it)) },
                onAddFromPreset = { d, presetId -> nav.navigate(Routes.newEntry(d, presetId)) },
                onEditEntry = { nav.navigate(Routes.editEntry(it)) },
                bottomBar = {},
            )
        }
        composable(Routes.WEEK) {
            val vm: WeekViewModel = viewModel(factory = viewModelFactory { initializer { WeekViewModel(container) } })
            WeekScreen(
                viewModel = vm,
                onOpenDay = { date ->
                    if (date == container.time.today()) nav.goToToday() else nav.navigate(Routes.day(date))
                },
                bottomBar = bottomBar,
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
            SettingsScreen(
                viewModel = vm,
                onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                onDataCleared = { nav.goToToday() },
                bottomBar = bottomBar,
            )
        }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.ENTRY,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
                navArgument("preset") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val date = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val presetId = entry.arguments?.getLong("preset")?.takeIf { it > 0 }
            val vm: EntryEditorViewModel = viewModel(
                key = "entry-$id-$date-$presetId",
                factory = viewModelFactory {
                    initializer { EntryEditorViewModel(container, createSavedStateHandle(), id, date, presetId) }
                },
            )
            EntryEditorScreen(vm, onClose = { nav.popBackStack() })
        }
    }
}

@Composable
private fun AppNavigationBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = ShelfColors.CreamDeep) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = ShelfColors.AccentContainer),
            )
        }
    }
}

/** Top-level tabs keep Today at the bottom of the stack, so Back from Week/Settings returns to Today and Back from Today exits. */
private fun NavHostController.openTab(tab: Tab) {
    if (tab == Tab.TODAY) {
        goToToday()
        return
    }
    navigate(tab.route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.goToToday() {
    if (!popBackStack(Routes.TODAY, inclusive = false)) {
        navigate(Routes.TODAY) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

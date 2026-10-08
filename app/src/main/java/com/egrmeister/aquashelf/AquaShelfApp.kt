package com.egrmeister.aquashelf

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.egrmeister.aquashelf.data.local.AppDatabase
import com.egrmeister.aquashelf.data.local.PreferencesStore
import com.egrmeister.aquashelf.data.repository.PresetRepository
import com.egrmeister.aquashelf.data.repository.WaterRepository
import com.egrmeister.aquashelf.data.repository.resetAllRecords
import com.egrmeister.aquashelf.domain.entries.ActionGuard
import com.egrmeister.aquashelf.domain.time.SystemTimeSource
import com.egrmeister.aquashelf.domain.time.TimeSource
import com.egrmeister.aquashelf.domain.time.TodayProvider
import com.egrmeister.aquashelf.reminders.Notifications
import com.egrmeister.aquashelf.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency injection: one container per process. */
class AppContainer(appContext: Context, val time: TimeSource = SystemTimeSource) {
    val context: Context = appContext.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database: AppDatabase = AppDatabase.create(context)
    val preferences = PreferencesStore(context)
    val entries = WaterRepository(database, time)
    val presets = PresetRepository(database)
    val todayProvider = TodayProvider(time, appScope)
    val reminderScheduler = ReminderScheduler(context, entries.service, preferences, time)
    val actionGuard = ActionGuard(clockMs = { SystemClock.elapsedRealtime() })

    /**
     * Clear All Data: cancels reminders and visible notifications, removes all records and
     * preferences (Room transaction + DataStore), then restores the starter presets.
     */
    suspend fun clearAllData() {
        reminderScheduler.cancelAll()
        database.resetAllRecords()
        preferences.clear(keepSeeded = true)
    }
}

class AquaShelfApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        container.appScope.launch {
            container.presets.seedOnFirstLaunch(container.preferences)
        }
    }
}

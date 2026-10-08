package com.egrmeister.aquashelf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.egrmeister.aquashelf.reminders.Notifications
import com.egrmeister.aquashelf.ui.AquaShelfRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Incremented whenever a reminder notification asks us to show Today. */
    private val openTodayRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val container = (application as AquaShelfApp).container
        setContent {
            AquaShelfRoot(container = container, openTodayRequests = openTodayRequests)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Foreground return: refresh Today (midnight may have passed) and re-plan the next reminder.
        val container = (application as AquaShelfApp).container
        container.todayProvider.refresh()
        lifecycleScope.launch { container.reminderScheduler.reschedule() }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(Notifications.EXTRA_OPEN_TODAY, false)) {
            openTodayRequests.value += 1
            intent.removeExtra(Notifications.EXTRA_OPEN_TODAY)
        }
    }
}

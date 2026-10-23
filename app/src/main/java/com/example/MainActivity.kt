package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ThemeMode
import com.example.notifications.ReminderScheduler
import com.example.ui.DayFlowApp
import com.example.ui.DayFlowViewModel
import com.example.ui.theme.DayFlowTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DayFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleReminderDeepLink(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DayFlowTheme(darkTheme = darkTheme) {
                DayFlowApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderDeepLink(intent)
    }

    private fun handleReminderDeepLink(intent: Intent?) {
        val reminderId = intent?.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId > 0L) {
            viewModel.openReminderById(reminderId)
            intent?.removeExtra(ReminderScheduler.EXTRA_REMINDER_ID)
        }
    }
}

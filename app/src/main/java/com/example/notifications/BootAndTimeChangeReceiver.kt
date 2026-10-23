package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.DayFlowDatabase
import com.example.data.ReminderRepository
import com.example.data.UserPreferencesRepository
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootAndTimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (action !in supportedActions) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val db = DayFlowDatabase.getInstance(appContext)
                val prefsRepo = UserPreferencesRepository(appContext)
                val scheduler = ReminderScheduler(appContext)
                val repository = ReminderRepository(db.reminderDao(), scheduler, prefsRepo)

                repository.reconcileAllRemindersWithSystemClock(ZoneId.systemDefault())
            } finally {
                pendingResult.finish()
            }
        }
    }
}

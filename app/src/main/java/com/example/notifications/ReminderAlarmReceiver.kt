package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.DayFlowDatabase
import com.example.data.ReminderRepository
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val db = DayFlowDatabase.getInstance(appContext)
                val prefsRepo = UserPreferencesRepository(appContext)
                val scheduler = ReminderScheduler(appContext)
                val repository = ReminderRepository(db.reminderDao(), scheduler, prefsRepo)
                val settings = prefsRepo.settingsFlow.first()

                when (action) {
                    ReminderScheduler.ACTION_FIRE_REMINDER -> {
                        val offsetMin = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, 0)
                        val reminder = repository.getReminderByIdSync(reminderId)
                        if (
                            reminder != null &&
                            !reminder.isCompleted &&
                            reminder.notificationEnabled &&
                            !reminder.isRecurrencePaused
                        ) {
                            scheduler.showReminderNotification(
                                reminder = reminder,
                                offsetMinutes = offsetMin,
                                use24Hour = settings.use24HourFormat,
                                soundEnabled = settings.soundEnabled,
                                vibrationEnabled = settings.vibrationEnabled
                            )
                        }
                    }

                    ReminderScheduler.ACTION_COMPLETE_REMINDER -> {
                        repository.setReminderCompleted(reminderId, true)
                    }

                    ReminderScheduler.ACTION_SNOOZE_REMINDER -> {
                        val snoozeMinutes = intent.getIntExtra(
                            ReminderScheduler.EXTRA_SNOOZE_MINUTES,
                            settings.defaultSnoozeMinutes
                        )
                        scheduler.cancelReminderAlarms(reminderId)
                        repository.snoozeReminder(reminderId, snoozeMinutes)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

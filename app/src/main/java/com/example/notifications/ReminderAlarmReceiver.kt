package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.DayFlowDatabase
import com.example.data.ReminderRepository
import com.example.data.UserPreferencesRepository
import com.example.ui.AlarmActivity
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
                val repository = ReminderRepository(db.reminderDao(), scheduler, prefsRepo, db.taskDao())
                val settings = prefsRepo.settingsFlow.first()

                when (action) {
                    ReminderScheduler.ACTION_FIRE_REMINDER -> {
                        val offsetMin = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, 0)
                        val isRingingFromIntent = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_RINGING_ALARM, false)
                        val reminder = repository.getReminderByIdSync(reminderId)

                        if (
                            reminder != null &&
                            !reminder.isCompleted &&
                            reminder.notificationEnabled &&
                            !reminder.isRecurrencePaused
                        ) {
                            val shouldRing = (reminder.isRingingAlarm || isRingingFromIntent) && offsetMin == 0

                            // Record firing history to track actual vs scheduled timing & battery delays
                            val actualTriggerTime = System.currentTimeMillis()
                            val scheduledTime = if (offsetMin > 0) {
                                reminder.scheduledEpochMillis - (offsetMin * 60_000L)
                            } else {
                                reminder.snoozedUntilEpochMillis ?: reminder.scheduledEpochMillis
                            }
                            val delaySec = ((actualTriggerTime - scheduledTime) / 1000L).coerceAtLeast(0L)
                            val historyEntry = com.example.data.AlarmHistoryEntity(
                                reminderId = reminder.id,
                                taskTitle = reminder.title,
                                scheduledEpochMillis = scheduledTime,
                                actualTriggerEpochMillis = actualTriggerTime,
                                delaySeconds = delaySec,
                                isRinging = shouldRing,
                                triggerSource = if (shouldRing) "ALARM_CLOCK" else "NOTIFICATION_BANNER"
                            )
                            try {
                                db.alarmHistoryDao().insert(historyEntry)
                                db.alarmHistoryDao().pruneToLastTen()
                            } catch (_: Exception) {}

                            if (shouldRing) {
                                // 1. Start continuous foreground AlarmService for audio & vibration
                                val serviceIntent = Intent(appContext, AlarmService::class.java).apply {
                                    this.action = AlarmService.ACTION_START_ALARM
                                    putExtra(AlarmService.EXTRA_REMINDER_ID, reminder.id)
                                    putExtra(AlarmService.EXTRA_TITLE, reminder.title)
                                    putExtra(AlarmService.EXTRA_DESCRIPTION, reminder.description)
                                    putExtra(AlarmService.EXTRA_SOUND_URI, reminder.alarmSoundUri)
                                    putExtra(AlarmService.EXTRA_VIBRATE, reminder.alarmVibration && settings.vibrationEnabled)
                                    putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, reminder.snoozeDurationMinutes)
                                }
                                try {
                                    ContextCompat.startForegroundService(appContext, serviceIntent)
                                } catch (_: Exception) {
                                    appContext.startService(serviceIntent)
                                }

                                // 2. Launch full-screen AlarmActivity to turn screen on & display over lock screen
                                val activityIntent = Intent(appContext, AlarmActivity::class.java).apply {
                                    putExtra(AlarmService.EXTRA_REMINDER_ID, reminder.id)
                                    putExtra(AlarmService.EXTRA_TITLE, reminder.title)
                                    putExtra(AlarmService.EXTRA_DESCRIPTION, reminder.description)
                                    putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, reminder.snoozeDurationMinutes)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                try {
                                    appContext.startActivity(activityIntent)
                                } catch (_: Exception) {}
                            } else {
                                // Standard notification banner for advance alerts or non-ringing tasks
                                scheduler.showReminderNotification(
                                    reminder = reminder,
                                    offsetMinutes = offsetMin,
                                    use24Hour = settings.use24HourFormat,
                                    soundEnabled = settings.soundEnabled,
                                    vibrationEnabled = settings.vibrationEnabled
                                )
                            }
                        }
                    }

                    ReminderScheduler.ACTION_COMPLETE_REMINDER -> {
                        if (AlarmService.isRinging && AlarmService.activeReminderId == reminderId) {
                            try {
                                val stopIntent = Intent(appContext, AlarmService::class.java).apply {
                                    this.action = AlarmService.ACTION_STOP_ALARM
                                }
                                appContext.startService(stopIntent)
                            } catch (_: Exception) {}
                        }
                        repository.setReminderCompleted(reminderId, true)
                    }

                    ReminderScheduler.ACTION_SNOOZE_REMINDER -> {
                        val snoozeMinutes = intent.getIntExtra(
                            ReminderScheduler.EXTRA_SNOOZE_MINUTES,
                            settings.defaultSnoozeMinutes
                        )
                        if (AlarmService.isRinging && AlarmService.activeReminderId == reminderId) {
                            try {
                                val stopIntent = Intent(appContext, AlarmService::class.java).apply {
                                    this.action = AlarmService.ACTION_STOP_ALARM
                                }
                                appContext.startService(stopIntent)
                            } catch (_: Exception) {}
                        }
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

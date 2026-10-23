package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DateTimeUtils
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import java.util.Locale

data class NotificationDiagnostics(
    val postNotificationsGranted: Boolean,
    val systemNotificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val highPriorityChannelEnabled: Boolean,
    val standardChannelEnabled: Boolean,
    val activeScheduledRemindersCount: Int,
    val totalPendingTriggersCount: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val oemPowerGuidance: String
) {
    val isFullyOperational: Boolean
        get() = postNotificationsGranted &&
            systemNotificationsEnabled &&
            highPriorityChannelEnabled &&
            standardChannelEnabled
}

class ReminderScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_HIGH_ID = "dayflow_high_priority"
        const val CHANNEL_STANDARD_ID = "dayflow_standard_reminders"

        const val ACTION_FIRE_REMINDER = "com.example.dayflow.ACTION_FIRE_REMINDER"
        const val ACTION_COMPLETE_REMINDER = "com.example.dayflow.ACTION_COMPLETE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.example.dayflow.ACTION_SNOOZE_REMINDER"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_IS_RINGING_ALARM = "extra_is_ringing_alarm"
        const val EXTRA_IS_TEST = "extra_is_test"

        private const val MAX_OFFSET_SLOTS = 10
        const val TEST_NOTIFICATION_ID = 999_001
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureNotificationChannels()
    }

    fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val highChannel = NotificationChannel(
                CHANNEL_HIGH_ID,
                context.getString(R.string.channel_reminders_high_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_reminders_high_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 280, 140, 280)
                setShowBadge(true)
            }

            val standardChannel = NotificationChannel(
                CHANNEL_STANDARD_ID,
                context.getString(R.string.channel_reminders_standard_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_reminders_standard_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(highChannel)
            notificationManager.createNotificationChannel(standardChannel)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Schedules all required triggers (primary + advance alerts, or snoozed time) for [reminder].
     * First cancels any existing alarms for this reminder ID to avoid duplicate notifications.
     */
    fun scheduleReminder(reminder: ReminderEntity, nowMillis: Long = System.currentTimeMillis()): Int {
        cancelReminderAlarms(reminder.id)

        if (reminder.isCompleted || !reminder.notificationEnabled || reminder.isRecurrencePaused) {
            return 0
        }

        val am = alarmManager ?: return 0
        var scheduledCount = 0

        // If snoozed in the future, schedule the snooze trigger at slot 0
        val snoozedUntil = reminder.snoozedUntilEpochMillis
        if (snoozedUntil != null && snoozedUntil > nowMillis) {
            scheduleSingleTrigger(
                am = am,
                reminderId = reminder.id,
                slotIndex = 0,
                triggerAtMillis = snoozedUntil,
                offsetMinutes = 0,
                isRingingAlarm = reminder.isRingingAlarm
            )
            scheduledCount++
            return scheduledCount
        }

        val offsets = reminder.parsedAdvanceOffsets().take(MAX_OFFSET_SLOTS)
        offsets.forEachIndexed { index, offsetMin ->
            val triggerAt = reminder.scheduledEpochMillis - (offsetMin * 60_000L)
            if (triggerAt > nowMillis - 30_000L) {
                val safeTriggerAt = triggerAt.coerceAtLeast(nowMillis + 1_500L)
                scheduleSingleTrigger(
                    am = am,
                    reminderId = reminder.id,
                    slotIndex = index,
                    triggerAtMillis = safeTriggerAt,
                    offsetMinutes = offsetMin,
                    isRingingAlarm = if (offsetMin == 0) reminder.isRingingAlarm else false
                )
                scheduledCount++
            }
        }
        return scheduledCount
    }

    private fun scheduleSingleTrigger(
        am: AlarmManager,
        reminderId: Long,
        slotIndex: Int,
        triggerAtMillis: Long,
        offsetMinutes: Int,
        isRingingAlarm: Boolean = false
    ) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_OFFSET_MINUTES, offsetMinutes)
            putExtra(EXTRA_IS_RINGING_ALARM, isRingingAlarm)
        }
        val requestCode = computeRequestCode(reminderId, slotIndex)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (isRingingAlarm && offsetMinutes == 0) {
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode + 50000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                am.setAlarmClock(clockInfo, pendingIntent)
            } else if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            // Fallback to inexact idle-aware alarm if OS revokes exact alarm permission mid-flight
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancels all scheduled PendingIntents for [reminderId] and dismisses any visible notification.
     */
    fun cancelReminderAlarms(reminderId: Long) {
        if (AlarmService.isRinging && AlarmService.activeReminderId == reminderId) {
            try {
                val stopServiceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP_ALARM
                }
                context.startService(stopServiceIntent)
            } catch (_: Exception) {}
        }

        val am = alarmManager
        if (am != null) {
            for (slot in 0 until MAX_OFFSET_SLOTS) {
                val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ACTION_FIRE_REMINDER
                }
                val requestCode = computeRequestCode(reminderId, slot)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    am.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
        notificationManager.cancel(computeNotificationId(reminderId))
    }

    fun showReminderNotification(
        reminder: ReminderEntity,
        offsetMinutes: Int = 0,
        use24Hour: Boolean = false,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ) {
        ensureNotificationChannels()
        if (!hasNotificationPermission()) return

        val notificationId = computeNotificationId(reminder.id)
        val channelId = if (reminder.priority == ReminderPriority.HIGH) {
            CHANNEL_HIGH_ID
        } else {
            CHANNEL_STANDARD_ID
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Complete Action
        val completeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_COMPLETE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 50_000,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 10m Action
        val snooze10Intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_SNOOZE_MINUTES, 10)
        }
        val snooze10PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 60_000,
            snooze10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 30m Action
        val snooze30Intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_SNOOZE_MINUTES, 30)
        }
        val snooze30PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 70_000,
            snooze30Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormatted = DateTimeUtils.formatDisplayTime(reminder.localTimeStr, use24Hour)
        val prefix = if (offsetMinutes > 0) "In $offsetMinutes min ($timeFormatted)" else "Due at $timeFormatted"
        val subtitle = buildString {
            append(prefix)
            append(" • ")
            append(reminder.category.label)
            if (reminder.isRecurring) {
                append(" • ")
                append(DateTimeUtils.formatRecurrenceSummary(reminder))
            }
        }
        val bigText = if (reminder.description.isNotBlank()) {
            "$subtitle\n${reminder.description}"
        } else {
            subtitle
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(reminder.title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (reminder.priority == ReminderPriority.HIGH) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .addAction(
                android.R.drawable.checkbox_on_background,
                context.getString(R.string.action_complete),
                completePendingIntent
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.action_snooze_10),
                snooze10PendingIntent
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.action_snooze_30),
                snooze30PendingIntent
            )

        if (!soundEnabled) {
            builder.setSilent(true)
        }
        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 120, 250))
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled via diagnostics UI
        }
    }

    fun sendImmediateTestNotification(): Boolean {
        ensureNotificationChannels()
        if (!hasNotificationPermission()) return false

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exactStatus = if (canScheduleExactAlarms()) "Exact Alarms Ready" else "Standard Idle Alarms Active"
        val builder = NotificationCompat.Builder(context, CHANNEL_HIGH_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("DayFlow Native Notification Verified")
            .setContentText("Background delivery & channels are working ($exactStatus).")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your DayFlow notification channel is active! Scheduled reminders use native Android AlarmManager and automatically recover after device reboots."
                )
            )
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        return try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun getDiagnostics(activeReminders: List<ReminderEntity>): NotificationDiagnostics {
        ensureNotificationChannels()
        val now = System.currentTimeMillis()
        val eligibleReminders = activeReminders.filter {
            !it.isCompleted && it.notificationEnabled && !it.isRecurrencePaused
        }
        var totalTriggers = 0
        eligibleReminders.forEach { reminder ->
            val snoozed = reminder.snoozedUntilEpochMillis
            if (snoozed != null && snoozed > now) {
                totalTriggers += 1
            } else {
                totalTriggers += reminder.parsedAdvanceOffsets().count { offset ->
                    (reminder.scheduledEpochMillis - offset * 60_000L) > now
                }
            }
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        var highEnabled = true
        var standardEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val highChannel = notificationManager.getNotificationChannel(CHANNEL_HIGH_ID)
            val stdChannel = notificationManager.getNotificationChannel(CHANNEL_STANDARD_ID)
            highEnabled = highChannel?.importance != NotificationManager.IMPORTANCE_NONE
            standardEnabled = stdChannel?.importance != NotificationManager.IMPORTANCE_NONE
        }

        val manufacturer = Build.MANUFACTURER ?: "Android"
        val oemGuidance = buildOemGuidance(manufacturer)

        return NotificationDiagnostics(
            postNotificationsGranted = hasNotificationPermission(),
            systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarmsAllowed = canScheduleExactAlarms(),
            ignoringBatteryOptimizations = ignoringBattery,
            highPriorityChannelEnabled = highEnabled,
            standardChannelEnabled = standardEnabled,
            activeScheduledRemindersCount = eligibleReminders.size,
            totalPendingTriggersCount = totalTriggers,
            deviceManufacturer = manufacturer,
            deviceModel = Build.MODEL ?: "Device",
            androidRelease = Build.VERSION.RELEASE ?: "${Build.VERSION.SDK_INT}",
            sdkInt = Build.VERSION.SDK_INT,
            oemPowerGuidance = oemGuidance
        )
    }

    private fun buildOemGuidance(manufacturer: String): String {
        val m = manufacturer.lowercase(Locale.US)
        return when {
            "samsung" in m -> "Samsung One UI: If reminders are delayed during deep sleep, open App Info → Battery → select 'Unrestricted', and ensure DayFlow is not in 'Deep sleeping apps'."
            "xiaomi" in m || "redmi" in m || "poco" in m -> "Xiaomi HyperOS / MIUI: Enable 'Autostart' in App Info and set Battery Saver to 'No restrictions' so reboot recovery and background alarms are not blocked."
            "oneplus" in m || "oppo" in m || "realme" in m -> "ColorOS / OxygenOS: In App Info → Battery usage, allow 'Background activity' and 'Auto-launch' for reliable overnight scheduling."
            "huawei" in m || "honor" in m -> "EMUI / MagicOS: In App Launch settings, switch DayFlow to 'Manage manually' with Auto-launch and Run in background enabled."
            else -> "Standard Android Power Management: DayFlow uses RTC_WAKEUP alarms with Doze-aware scheduling. Note that manually choosing 'Force Stop' in system settings disables all alarms until you reopen DayFlow."
        }
    }

    fun computeNotificationId(reminderId: Long): Int {
        return (reminderId % 100_000L).toInt().coerceAtLeast(1)
    }

    private fun computeRequestCode(reminderId: Long, slotIndex: Int): Int {
        return computeNotificationId(reminderId) * 100 + slotIndex
    }
}

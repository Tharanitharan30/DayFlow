package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.ReminderCategory
import com.example.data.ReminderPriority
import com.example.data.ThemeMode
import com.example.data.UserSettings
import com.example.notifications.NotificationDiagnostics
import com.example.ui.theme.CompletedSuccessColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndDiagnosticsScreen(
    settings: UserSettings,
    diagnostics: NotificationDiagnostics?,
    onRequestNotificationPermission: () -> Unit,
    onSendTestNotification: () -> Unit,
    onReconcileAlarms: () -> Unit,
    onOpenDiagnosticsModal: () -> Unit,
    onOpenJsonBackupDialog: () -> Unit,
    onExportJsonToFile: () -> Unit,
    onImportJsonFromFile: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetUse24Hour: (Boolean) -> Unit,
    onSetWeekStartsMonday: (Boolean) -> Unit,
    onSetDefaultPriority: (ReminderPriority) -> Unit,
    onSetDefaultCategory: (ReminderCategory) -> Unit,
    onSetDefaultAdvanceMinutes: (Int) -> Unit,
    onSetSoundEnabled: (Boolean) -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onReopenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Reliability",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Configure native notifications, preferences, and offline database backups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Notification Diagnostics & Reliability Center Card
        item {
            NotificationDiagnosticsCard(
                diagnostics = diagnostics,
                lastReconciledMillis = settings.lastReconciledEpochMillis,
                use24Hour = settings.use24HourFormat,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenExactAlarmSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                },
                onOpenSystemNotificationSettings = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                onSendTestNotification = onSendTestNotification,
                onReconcileAlarms = onReconcileAlarms,
                onOpenFullDiagnostics = onOpenDiagnosticsModal
            )
        }

        // 2. Appearance & Regional Formatting
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Appearance & Calendar Format",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text("Theme Preference", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("24-Hour Time Format", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Display 14:30 instead of 2:30 PM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.use24HourFormat,
                            onCheckedChange = onSetUse24Hour,
                            modifier = Modifier.testTag("switch_24_hour")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Week on Monday", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Applies to Monthly and Weekly calendar grids",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.weekStartsOnMonday,
                            onCheckedChange = onSetWeekStartsMonday,
                            modifier = Modifier.testTag("switch_week_starts_monday")
                        )
                    }
                }
            }
        }

        // 3. Default Reminder Behavior & Alert Sound/Vibration
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Default Reminder Behavior",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text("Default Priority", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReminderPriority.entries.forEach { prio ->
                            FilterChip(
                                selected = settings.defaultPriority == prio,
                                onClick = { onSetDefaultPriority(prio) },
                                label = { Text(prio.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Default Advance Alert", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "On time only", 5 to "5m early", 10 to "10m early", 30 to "30m early", 60 to "1h early")
                            .forEach { (mins, label) ->
                                FilterChip(
                                    selected = settings.defaultAdvanceMinutes == mins,
                                    onClick = { onSetDefaultAdvanceMinutes(mins) },
                                    label = { Text(label) }
                                )
                            }
                    }

                    Text("Default Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReminderCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = settings.defaultCategory == cat,
                                onClick = { onSetDefaultCategory(cat) },
                                label = { Text(cat.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Sound", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Play alert tone when reminders trigger",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = onSetSoundEnabled
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibration Feedback", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Vibrate device on scheduled reminders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = onSetVibrationEnabled
                        )
                    }
                }
            }
        }

        // 4. Local SQLite Database Export & Import (JSON)
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Offline Database Backup & Restore (JSON)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "All reminders are stored locally in an encrypted-ready Room SQLite database. Export or import your reminders as portable JSON anytime without an account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportJsonToFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_json_file_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save File")
                        }
                        OutlinedButton(
                            onClick = onImportJsonFromFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_json_file_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load File")
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenJsonBackupDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_json_inspector_button")
                    ) {
                        Text("Inspect / Copy / Paste Raw JSON Backup")
                    }
                }
            }
        }

        // 5. About DayFlow & Onboarding Guide
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "About DayFlow",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = "Version 1.0.0 • Native Android (Kotlin + Jetpack Compose + Room SQLite + AlarmManager)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "DayFlow schedules native RTC_WAKEUP alarms with boot recovery (BOOT_COMPLETED) and automatic timezone/DST reconciliation. Please note: Android OS power management, Doze mode, denied permissions, or manually choosing 'Force Stop' in system settings can delay or prevent alarms until the app is reopened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onReopenOnboarding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reopen_onboarding_button")
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Welcome & Setup Guide")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
private fun NotificationDiagnosticsCard(
    diagnostics: NotificationDiagnostics?,
    lastReconciledMillis: Long,
    use24Hour: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onSendTestNotification: () -> Unit,
    onReconcileAlarms: () -> Unit,
    onOpenFullDiagnostics: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Notification Diagnostics",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${diagnostics?.activeScheduledRemindersCount ?: 0} active reminders (${diagnostics?.totalPendingTriggersCount ?: 0} alarm triggers)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onOpenFullDiagnostics) {
                    Text("Details")
                }
            }

            if (diagnostics != null) {
                DiagnosticRowItem(
                    label = "Android Notification Permission",
                    isHealthy = diagnostics.postNotificationsGranted && diagnostics.systemNotificationsEnabled,
                    statusText = if (diagnostics.postNotificationsGranted && diagnostics.systemNotificationsEnabled) {
                        "Granted"
                    } else {
                        "Action Required"
                    },
                    actionLabel = if (!diagnostics.postNotificationsGranted) "Grant" else null,
                    onAction = onRequestNotificationPermission
                )

                DiagnosticRowItem(
                    label = "Exact Alarm Scheduling (RTC_WAKEUP)",
                    isHealthy = diagnostics.exactAlarmsAllowed,
                    statusText = if (diagnostics.exactAlarmsAllowed) {
                        "Exact Allowed"
                    } else {
                        "Fallback Idle Mode"
                    },
                    actionLabel = if (!diagnostics.exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Allow Exact"
                    } else {
                        null
                    },
                    onAction = onOpenExactAlarmSettings
                )

                DiagnosticRowItem(
                    label = "Notification Channels (High & Standard)",
                    isHealthy = diagnostics.highPriorityChannelEnabled && diagnostics.standardChannelEnabled,
                    statusText = if (diagnostics.highPriorityChannelEnabled && diagnostics.standardChannelEnabled) {
                        "Active"
                    } else {
                        "Muted in OS"
                    },
                    actionLabel = if (!diagnostics.highPriorityChannelEnabled || !diagnostics.standardChannelEnabled) {
                        "Unmute"
                    } else {
                        null
                    },
                    onAction = onOpenSystemNotificationSettings
                )

                // OEM Battery guidance banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${diagnostics.deviceManufacturer} (${diagnostics.deviceModel}) • Android ${diagnostics.androidRelease}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = diagnostics.oemPowerGuidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (lastReconciledMillis > 0L) {
                Text(
                    text = "Last alarm schedule reconciliation: ${
                        DateTimeUtils.formatEpochTime(lastReconciledMillis, use24Hour)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onReconcileAlarms,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reconcile_alarms_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reschedule All")
                }

                Button(
                    onClick = onSendTestNotification,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("send_test_notification_button")
                ) {
                    Icon(Icons.Default.AlarmOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Alert")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRowItem(
    label: String,
    isHealthy: Boolean,
    statusText: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (isHealthy) CompletedSuccessColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isHealthy) CompletedSuccessColor else MaterialTheme.colorScheme.error
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsModalSheet(
    diagnostics: NotificationDiagnostics?,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onSendTest: () -> Unit,
    onReconcile: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Native Notification Diagnostics",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "DayFlow uses Android's native AlarmManager and BroadcastReceivers rather than web timers or background polling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (diagnostics != null) {
                DiagnosticRowItem(
                    label = "POST_NOTIFICATIONS Permission",
                    isHealthy = diagnostics.postNotificationsGranted,
                    statusText = if (diagnostics.postNotificationsGranted) "Granted" else "Not Granted",
                    actionLabel = if (!diagnostics.postNotificationsGranted) "Request" else null,
                    onAction = onRequestPermission
                )
                DiagnosticRowItem(
                    label = "Exact Alarm Capability (API ${diagnostics.sdkInt})",
                    isHealthy = diagnostics.exactAlarmsAllowed,
                    statusText = if (diagnostics.exactAlarmsAllowed) {
                        "canScheduleExactAlarms() = true"
                    } else {
                        "Using Doze-aware setAndAllowWhileIdle fallback"
                    }
                )
                DiagnosticRowItem(
                    label = "Battery Optimization Exemption",
                    isHealthy = diagnostics.ignoringBatteryOptimizations,
                    statusText = if (diagnostics.ignoringBatteryOptimizations) {
                        "Unrestricted"
                    } else {
                        "Managed by Android Doze (Standard)"
                    }
                )
                Text(
                    text = "Known Android Platform Limitations:\n" +
                        "• Force-stopping the app in Android Settings clears all PendingIntents until DayFlow is launched again.\n" +
                        "• Extreme OEM battery savers may defer alarms during deep idle windows.\n" +
                        "• Reboot recovery runs automatically via BOOT_COMPLETED after the device unlocks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReconcile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reconcile Alarms")
                }
                Button(
                    onClick = onSendTest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send Test Alert")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun JsonBackupRestoreDialog(
    initialJson: String,
    onDismiss: () -> Unit,
    onImportJson: (String, Boolean) -> Unit,
    onCopiedMessage: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var jsonText by remember(initialJson) { mutableStateOf(initialJson) }
    var replaceExisting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("JSON Database Export / Import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Copy this JSON to back up your reminders, or paste a DayFlow JSON backup below to restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("json_backup_textfield")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replace existing reminders on import",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(
                        checked = replaceExisting,
                        onCheckedChange = { replaceExisting = it }
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(jsonText))
                        onCopiedMessage()
                    }
                ) {
                    Text("Copy JSON")
                }
                Button(
                    onClick = { onImportJson(jsonText, replaceExisting) },
                    modifier = Modifier.testTag("json_import_confirm_button")
                ) {
                    Text("Import JSON")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

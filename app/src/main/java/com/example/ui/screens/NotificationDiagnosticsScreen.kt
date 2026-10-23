package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AlarmHistoryEntity
import com.example.data.DateTimeUtils
import com.example.data.DelaySeverity
import com.example.notifications.NotificationDiagnostics
import com.example.ui.theme.CompletedSuccessColor
import com.example.ui.theme.PriorityMediumColor
import java.util.Locale

private data class OemBatteryProfile(
    val id: String,
    val brandTitle: String,
    val osName: String,
    val steps: List<String>
)

private val OemGuides = listOf(
    OemBatteryProfile(
        id = "samsung",
        brandTitle = "Samsung",
        osName = "One UI",
        steps = listOf(
            "Open Settings → Apps → DayFlow → Battery and select 'Unrestricted'.",
            "Open Settings → Battery and device care → Battery → Background usage limits.",
            "Ensure DayFlow is NOT listed in 'Sleeping apps' or 'Deep sleeping apps'."
        )
    ),
    OemBatteryProfile(
        id = "xiaomi",
        brandTitle = "Xiaomi / Redmi / POCO",
        osName = "HyperOS & MIUI",
        steps = listOf(
            "Open Settings → Apps → Manage apps → DayFlow.",
            "Enable the 'Autostart' (Background autostart) toggle so reboot recovery runs reliably.",
            "Tap 'Battery saver' and select 'No restrictions'."
        )
    ),
    OemBatteryProfile(
        id = "oneplus",
        brandTitle = "OnePlus / Oppo / Realme",
        osName = "OxygenOS & ColorOS",
        steps = listOf(
            "Open Settings → Apps → App Management → DayFlow → Battery usage.",
            "Enable 'Allow background activity' and 'Allow auto-launch'.",
            "Avoid locking/force-closing the app from system settings."
        )
    ),
    OemBatteryProfile(
        id = "huawei",
        brandTitle = "Huawei / Honor",
        osName = "EMUI & MagicOS",
        steps = listOf(
            "Open Settings → Battery → App launch.",
            "Find DayFlow, turn off 'Manage automatically', and enable 'Auto-launch', 'Secondary launch', and 'Run in background'."
        )
    ),
    OemBatteryProfile(
        id = "pixel",
        brandTitle = "Google Pixel / Standard Android",
        osName = "Stock Android (Doze Mode)",
        steps = listOf(
            "DayFlow uses Doze-aware RTC_WAKEUP alarms that wake the device during maintenance windows or exact alarm slots.",
            "For time-critical alerts during deep idle, set App Battery usage to 'Unrestricted'.",
            "Note: Manually tapping 'Force stop' in App Info cancels all OS alarms until you reopen DayFlow."
        )
    )
)

enum class DiagnosticsTab(val label: String) {
    SYSTEM_STATUS("System & OEM"),
    RINGING_HISTORY("Ringing History")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationDiagnosticsScreen(
    diagnostics: NotificationDiagnostics?,
    lastReconciledMillis: Long,
    use24Hour: Boolean,
    alarmHistory: List<AlarmHistoryEntity> = emptyList(),
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSendTestNotification: () -> Unit,
    onTriggerTestRingingAlarm: () -> Unit = {},
    onClearAlarmHistory: () -> Unit = {},
    onReconcileAlarms: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val detectedOemId = remember(diagnostics?.deviceManufacturer) {
        val m = diagnostics?.deviceManufacturer?.lowercase(Locale.US).orEmpty()
        when {
            "samsung" in m -> "samsung"
            "xiaomi" in m || "redmi" in m || "poco" in m -> "xiaomi"
            "oneplus" in m || "oppo" in m || "realme" in m -> "oneplus"
            "huawei" in m || "honor" in m -> "huawei"
            else -> "pixel"
        }
    }
    var selectedOemId by remember(detectedOemId) { mutableStateOf(detectedOemId) }
    val activeOemProfile = remember(selectedOemId) {
        OemGuides.firstOrNull { it.id == selectedOemId } ?: OemGuides.last()
    }

    val openAppNotificationSettings = {
        runCatching {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    val openExactAlarmSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
        }
    }

    val openBatteryOptimizationSettings = {
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    val openAppInfoSettings = {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    var selectedTab by remember { mutableStateOf(DiagnosticsTab.SYSTEM_STATUS) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("notification_diagnostics_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with Back Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("diagnostics_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Diagnostics",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Permission status, exact-alarm access & OEM battery health",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Diagnostics Tabs: System & OEM vs Ringing History
        item {
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("diagnostics_tab_row")
            ) {
                Tab(
                    selected = selectedTab == DiagnosticsTab.SYSTEM_STATUS,
                    onClick = { selectedTab = DiagnosticsTab.SYSTEM_STATUS },
                    text = { Text("System & OEM", maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_diagnostics_system")
                )
                Tab(
                    selected = selectedTab == DiagnosticsTab.RINGING_HISTORY,
                    onClick = { selectedTab = DiagnosticsTab.RINGING_HISTORY },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Ringing History", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            if (alarmHistory.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Text(
                                        text = "${alarmHistory.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_diagnostics_history")
                )
            }
        }

        if (selectedTab == DiagnosticsTab.SYSTEM_STATUS) {
        // Overall System Status Hero Banner
        item {
            val isHealthy = diagnostics?.isFullyOperational == true
            val exactReady = diagnostics?.exactAlarmsAllowed == true
            val bannerGradient = Brush.linearGradient(
                colors = if (isHealthy) {
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                } else {
                    listOf(
                        MaterialTheme.colorScheme.error,
                        PriorityMediumColor
                    )
                }
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerGradient)
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isHealthy) {
                                    Icons.Default.VerifiedUser
                                } else {
                                    Icons.Default.WarningAmber
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (isHealthy && exactReady) {
                                        "All Notification Systems Ready"
                                    } else if (isHealthy) {
                                        "Notifications Enabled (Idle-Fallback Mode)"
                                    } else {
                                        "Permission or Channel Action Required"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "${diagnostics?.activeScheduledRemindersCount ?: 0} active reminders • ${diagnostics?.totalPendingTriggersCount ?: 0} scheduled AlarmManager triggers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }

                        if (lastReconciledMillis > 0L) {
                            Text(
                                text = "Last schedule reconciliation: ${
                                    DateTimeUtils.formatEpochTime(lastReconciledMillis, use24Hour)
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onSendTestNotification,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("diag_screen_test_alert_button")
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Banner", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = onTriggerTestRingingAlarm,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("diag_screen_test_ringing_button")
                            ) {
                                Icon(
                                    Icons.Default.AlarmOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Alarm", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = onReconcileAlarms,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("diag_screen_reconcile_button")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reschedule", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // 1. Notification Permission Card
        item {
            val postGranted = diagnostics?.postNotificationsGranted == true
            val osEnabled = diagnostics?.systemNotificationsEnabled == true
            val channelsOk = diagnostics?.highPriorityChannelEnabled == true &&
                diagnostics.standardChannelEnabled

            DiagnosticDetailCard(
                icon = Icons.Default.NotificationsActive,
                title = "1. Android Notification Permission & Channels",
                statusLabel = when {
                    postGranted && osEnabled && channelsOk -> "Granted & Active"
                    !postGranted -> "Permission Denied"
                    !osEnabled -> "Blocked in System Settings"
                    else -> "Channel Muted"
                },
                isHealthy = postGranted && osEnabled && channelsOk,
                explanation = "Android 13+ requires explicit runtime permission (POST_NOTIFICATIONS) to display reminder alerts and interactive 'Complete' / 'Snooze' buttons in the notification shade. Both High Priority and Standard channels must remain unmuted.",
                primaryActionLabel = if (!postGranted) "Grant Notification Permission" else null,
                onPrimaryAction = { onRequestNotificationPermission() },
                secondaryActionLabel = "Open Notification Settings",
                onSecondaryAction = { openAppNotificationSettings() }
            )
        }

        // 2. Exact Alarm Access Card
        item {
            val exactAllowed = diagnostics?.exactAlarmsAllowed == true
            DiagnosticDetailCard(
                icon = Icons.Default.Security,
                title = "2. Exact-Alarm Access (SCHEDULE_EXACT_ALARM)",
                statusLabel = if (exactAllowed) {
                    "Exact Alarms Allowed"
                } else {
                    "Restricted (Using Doze-Aware Fallback)"
                },
                isHealthy = exactAllowed,
                explanation = "To deliver reminders at the exact minute when your phone is asleep, DayFlow uses AlarmManager.setExactAndAllowWhileIdle(). On Android 12+, if 'Alarms & reminders' access is disabled, DayFlow automatically falls back to setAndAllowWhileIdle(), which may defer delivery by a few minutes during deep idle.",
                primaryActionLabel = if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Allow Exact Alarms in Settings"
                } else {
                    null
                },
                onPrimaryAction = { openExactAlarmSettings() },
                secondaryActionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Manage Exact Alarms"
                } else {
                    null
                },
                onSecondaryAction = { openExactAlarmSettings() }
            )
        }

        // 3. Battery Optimization Status Card
        item {
            val unrestricted = diagnostics?.ignoringBatteryOptimizations == true
            DiagnosticDetailCard(
                icon = if (unrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                title = "3. Android Doze & Battery Optimization",
                statusLabel = if (unrestricted) {
                    "Unrestricted Background Delivery"
                } else {
                    "Optimized (Standard Android Doze)"
                },
                isHealthy = true,
                statusAccentOverride = if (unrestricted) CompletedSuccessColor else PriorityMediumColor,
                explanation = "Android Doze conserves battery when your device is stationary and unplugged. DayFlow's RTC_WAKEUP alarms work with standard Doze, but if you experience delayed overnight alerts, setting DayFlow's battery usage to 'Unrestricted' prevents OEM power managers from deferring alarms.",
                primaryActionLabel = "Battery Optimization Settings",
                onPrimaryAction = { openBatteryOptimizationSettings() },
                secondaryActionLabel = "Open App Info",
                onSecondaryAction = { openAppInfoSettings() }
            )
        }

        // 4. Manufacturer-Specific (OEM) Battery Restrictions Guide
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "4. Manufacturer-Specific Battery Guides",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Detected Device: ${diagnostics?.deviceManufacturer ?: "Android"} ${diagnostics?.deviceModel ?: ""} (Android ${diagnostics?.androidRelease ?: ""})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Some Android manufacturers add custom background task killers that can block scheduled notifications or reboot recovery. Select your device brand below for optional reliability steps:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OemGuides.forEach { profile ->
                            FilterChip(
                                selected = selectedOemId == profile.id,
                                onClick = { selectedOemId = profile.id },
                                label = { Text(profile.brandTitle) },
                                modifier = Modifier.testTag("oem_chip_${profile.id}")
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${activeOemProfile.brandTitle} (${activeOemProfile.osName}) Recommendations",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            activeOemProfile.steps.forEachIndexed { index, stepText ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = stepText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        } else {
            // Ringing History Tab Content: Tracks last 10 alarms & battery delays
            item {
                RingingHistorySummaryCard(
                    history = alarmHistory,
                    onTriggerTestAlarm = onTriggerTestRingingAlarm,
                    onClearHistory = onClearAlarmHistory
                )
            }

            if (alarmHistory.isEmpty()) {
                item {
                    RingingHistoryEmptyCard(onTriggerTestAlarm = onTriggerTestRingingAlarm)
                }
            } else {
                items(
                    count = alarmHistory.size,
                    key = { alarmHistory[it].id }
                ) { index ->
                    AlarmHistoryItemCard(
                        entry = alarmHistory[index],
                        use24Hour = use24Hour
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
private fun DiagnosticDetailCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    statusLabel: String,
    isHealthy: Boolean,
    explanation: String,
    statusAccentOverride: Color? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val badgeColor = statusAccentOverride
        ?: if (isHealthy) CompletedSuccessColor else MaterialTheme.colorScheme.error

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isHealthy) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.WarningAmber
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor
                        )
                    }
                }
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (primaryActionLabel != null || secondaryActionLabel != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(secondaryActionLabel)
                        }
                    }
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        Button(
                            onClick = onPrimaryAction,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(primaryActionLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingingHistorySummaryCard(
    history: List<AlarmHistoryEntity>,
    onTriggerTestAlarm: () -> Unit,
    onClearHistory: () -> Unit
) {
    val total = history.size
    val onTimeCount = history.count { it.delaySeverity == DelaySeverity.ON_TIME }
    val minorCount = history.count { it.delaySeverity == DelaySeverity.MINOR }
    val severeCount = history.count { it.delaySeverity == DelaySeverity.SEVERE }
    val avgDelaySec = if (history.isNotEmpty()) history.map { it.delaySeconds }.average().toLong() else 0L
    val maxDelaySec = history.maxOfOrNull { it.delaySeconds } ?: 0L

    val isSevere = severeCount > 0
    val isMinor = minorCount > 0 && severeCount == 0

    val bannerGradient = Brush.linearGradient(
        colors = when {
            isSevere -> listOf(MaterialTheme.colorScheme.error, PriorityMediumColor)
            isMinor -> listOf(PriorityMediumColor, MaterialTheme.colorScheme.primary)
            else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
        }
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ringing_history_summary_card"),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerGradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isSevere -> Icons.Default.WarningAmber
                            isMinor -> Icons.Default.BatteryAlert
                            else -> Icons.Default.VerifiedUser
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = when {
                                total == 0 -> "No Ringing Events Tracked"
                                isSevere -> "Battery Delays Detected (> 3m delay)"
                                isMinor -> "Minor Idle Variances (< 3m delay)"
                                else -> "Optimal Delivery • 0 Battery Delays"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = if (total > 0) {
                                "$total recent alarms tracked • Avg delta: +${avgDelaySec}s • Max delay: +${maxDelaySec}s"
                            } else {
                                "Tracking last 10 alarms: scheduled vs actual firing times"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Text(
                    text = when {
                        total == 0 -> "When tasks trigger their scheduled alarms, DayFlow measures and logs the actual millisecond firing time to detect if Android Doze or OEM background task killers delayed the alarm."
                        isSevere -> "Severe alarm delays detected. Your OEM battery optimizer or Android power saver suspended background alarms. Please review the OEM Battery Guides in the 'System & OEM' tab."
                        isMinor -> "Minor delays observed during deep device idle. Setting DayFlow's battery usage to 'Unrestricted' will allow instant exact wakeups."
                        else -> "All recent alarms woke the CPU and fired on time via AlarmManager.setAlarmClock(). No battery restrictions or Doze deferrals detected."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onTriggerTestAlarm,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("history_test_alarm_button")
                    ) {
                        Icon(Icons.Default.AlarmOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Alarm Now")
                    }
                    if (total > 0) {
                        OutlinedButton(
                            onClick = onClearHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_clear_button"),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmHistoryItemCard(
    entry: AlarmHistoryEntity,
    use24Hour: Boolean
) {
    val severity = entry.delaySeverity
    val badgeColor = when (severity) {
        DelaySeverity.ON_TIME -> CompletedSuccessColor
        DelaySeverity.MINOR -> PriorityMediumColor
        DelaySeverity.SEVERE -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_history_item_${entry.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = if (entry.isRinging) Icons.Default.AlarmOn else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = entry.taskTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = if (entry.isRinging) "Clock Alarm (setAlarmClock)" else "Notification Banner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (severity) {
                                DelaySeverity.ON_TIME -> Icons.Default.CheckCircle
                                DelaySeverity.MINOR -> Icons.Default.Schedule
                                DelaySeverity.SEVERE -> Icons.Default.WarningAmber
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        val delayText = when {
                            entry.delaySeconds == 0L -> "Exact (0s)"
                            entry.delaySeconds < 60L -> "+${entry.delaySeconds}s"
                            else -> "+${entry.delaySeconds / 60}m ${entry.delaySeconds % 60}s"
                        }
                        Text(
                            text = "$delayText • ${severity.label}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SCHEDULED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = DateTimeUtils.formatEpochDateTimeWithSeconds(entry.scheduledEpochMillis, use24Hour),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ACTUAL FIRING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = DateTimeUtils.formatEpochDateTimeWithSeconds(entry.actualTriggerEpochMillis, use24Hour),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (severity == DelaySeverity.SEVERE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Battery diagnosis footnote
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (severity) {
                        DelaySeverity.ON_TIME -> "⚡ Immediate Wakeup: OS executed alarm clock trigger without battery optimization sleep delay."
                        DelaySeverity.MINOR -> "⏱️ Idle Variance: Slight delay (+${entry.delaySeconds}s) consistent with Android Doze maintenance window."
                        DelaySeverity.SEVERE -> "⚠️ Battery Delay Detected: Alarm fired ${entry.delaySeconds / 60}m late. Device power manager or background restriction held the CPU in sleep."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RingingHistoryEmptyCard(onTriggerTestAlarm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ringing_history_empty_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = "No Triggered Alarms Recorded Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "DayFlow automatically tracks the last 10 alarm and notification firing events, calculating the exact time delta between when an alarm was scheduled vs. when it actually rang. This helps diagnose OEM battery saver delays.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onTriggerTestAlarm,
                modifier = Modifier.testTag("empty_trigger_test_alarm_button")
            ) {
                Icon(Icons.Default.AlarmOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trigger Test Alarm")
            }
        }
    }
}


package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.ReminderEntity
import com.example.notifications.NotificationDiagnostics
import com.example.ui.components.ReminderCard
import com.example.ui.components.parseHexColor
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    reminders: List<ReminderEntity>,
    use24Hour: Boolean,
    diagnostics: NotificationDiagnostics?,
    onQuickAdd: (String) -> Unit,
    onOpenFullCreate: () -> Unit,
    onEditReminder: (ReminderEntity) -> Unit,
    onToggleComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity, Int) -> Unit,
    onTogglePauseRecurrence: (ReminderEntity) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val todayStr = DateTimeUtils.formatLocalDate(today)
    val nowMillis = System.currentTimeMillis()
    var quickAddText by remember { mutableStateOf("") }
    var showCompletedSection by remember { mutableStateOf(true) }

    val overdueTasks = remember(reminders, nowMillis) {
        reminders.filter {
            !it.isCompleted && it.effectiveTriggerMillis() < nowMillis
        }.sortedBy { it.effectiveTriggerMillis() }
    }

    val todayActiveTasks = remember(reminders, todayStr, nowMillis) {
        reminders.filter {
            !it.isCompleted &&
                it.localDateStr == todayStr &&
                it.effectiveTriggerMillis() >= nowMillis
        }.sortedBy { it.effectiveTriggerMillis() }
    }

    val upcomingTasks = remember(reminders, todayStr, nowMillis) {
        reminders.filter {
            !it.isCompleted &&
                it.localDateStr > todayStr &&
                it.effectiveTriggerMillis() >= nowMillis
        }.sortedBy { it.effectiveTriggerMillis() }
    }

    val completedTasks = remember(reminders) {
        reminders.filter { it.isCompleted }.sortedByDescending { it.completedAtEpochMillis ?: 0L }
    }

    val todayAllTasksCount = remember(reminders, todayStr) {
        reminders.count { it.localDateStr == todayStr }
    }
    val todayCompletedCount = remember(reminders, todayStr) {
        reminders.count { it.localDateStr == todayStr && it.isCompleted }
    }
    val completionFraction = if (todayAllTasksCount > 0) {
        todayCompletedCount.toFloat() / todayAllTasksCount.toFloat()
    } else if (completedTasks.isNotEmpty() && reminders.isNotEmpty()) {
        completedTasks.size.toFloat() / reminders.size.toFloat()
    } else {
        0f
    }

    val next24HoursEvents = remember(reminders, nowMillis) {
        val horizon = nowMillis + 24 * 3_600_000L
        reminders.filter {
            !it.isCompleted && it.effectiveTriggerMillis() in nowMillis..horizon
        }.sortedBy { it.effectiveTriggerMillis() }
    }

    val greeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Greeting & Date Header + Reliability Status Badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = DateTimeUtils.formatFullHeaderDate(today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isReady = diagnostics?.isFullyOperational == true
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .testTag("dashboard_diagnostics_badge")
                        .clickable(onClick = onOpenDiagnostics)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isReady) {
                                Icons.Default.VerifiedUser
                            } else {
                                Icons.Default.WarningAmber
                            },
                            contentDescription = "Notification status",
                            modifier = Modifier.size(16.dp),
                            tint = if (isReady) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Text(
                            text = if (isReady) {
                                "${diagnostics?.totalPendingTriggersCount ?: 0} Scheduled"
                            } else {
                                "Check Alerts"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isReady) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }
        }

        // Daily Progress Hero Card
        item {
            DailyProgressHeroCard(
                completionFraction = completionFraction,
                todayCompleted = todayCompletedCount,
                todayTotal = todayAllTasksCount,
                overdueCount = overdueTasks.size,
                upcomingCount = todayActiveTasks.size + upcomingTasks.size,
                onOpenCreate = onOpenFullCreate
            )
        }

        // Fast Inline Quick-Add Input Bar
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickAddText,
                        onValueChange = { quickAddText = it },
                        placeholder = { Text("Quick add a reminder for today...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_quick_add_input")
                    )
                    IconButton(
                        onClick = {
                            if (quickAddText.isNotBlank()) {
                                onQuickAdd(quickAddText)
                                quickAddText = ""
                            } else {
                                onOpenFullCreate()
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("dashboard_quick_add_submit")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Quick add reminder",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Compact Upcoming-Events 24h Timeline Strip
        if (next24HoursEvents.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Upcoming Timeline (Next 24h)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "${next24HoursEvents.size} event(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(next24HoursEvents, key = { "timeline_${it.id}" }) { event ->
                            CompactTimelineChip(
                                reminder = event,
                                use24Hour = use24Hour,
                                onClick = { onEditReminder(event) }
                            )
                        }
                    }
                }
            }
        }

        // Overdue Tasks Section
        if (overdueTasks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Overdue",
                    count = overdueTasks.size,
                    accentColor = MaterialTheme.colorScheme.error
                )
            }
            items(overdueTasks, key = { "overdue_${it.id}" }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    use24Hour = use24Hour,
                    nowMillis = nowMillis,
                    onToggleComplete = { onToggleComplete(reminder) },
                    onSnooze = { mins -> onSnooze(reminder, mins) },
                    onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                    onClick = { onEditReminder(reminder) }
                )
            }
        }

        // Today's Tasks Section
        item {
            SectionHeader(
                title = "Today's Schedule",
                count = todayActiveTasks.size,
                accentColor = MaterialTheme.colorScheme.primary
            )
        }
        if (todayActiveTasks.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All caught up for today!",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Tap + to schedule a new reminder or recurring habit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(todayActiveTasks, key = { "today_${it.id}" }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    use24Hour = use24Hour,
                    nowMillis = nowMillis,
                    onToggleComplete = { onToggleComplete(reminder) },
                    onSnooze = { mins -> onSnooze(reminder, mins) },
                    onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                    onClick = { onEditReminder(reminder) }
                )
            }
        }

        // Upcoming Reminders Section
        if (upcomingTasks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Upcoming Reminders",
                    count = upcomingTasks.size,
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
            }
            items(upcomingTasks.take(8), key = { "upcoming_${it.id}" }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    use24Hour = use24Hour,
                    nowMillis = nowMillis,
                    onToggleComplete = { onToggleComplete(reminder) },
                    onSnooze = { mins -> onSnooze(reminder, mins) },
                    onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                    onClick = { onEditReminder(reminder) }
                )
            }
        }

        // Completed Tasks Section
        if (completedTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { showCompletedSection = !showCompletedSection }
                    ) {
                        Text(
                            text = "Completed (${completedTasks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showCompletedSection) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = "Toggle completed",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onClearCompleted) {
                        Text("Clear Done")
                    }
                }
            }

            if (showCompletedSection) {
                items(completedTasks.take(6), key = { "completed_${it.id}" }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        use24Hour = use24Hour,
                        nowMillis = nowMillis,
                        onToggleComplete = { onToggleComplete(reminder) },
                        onSnooze = { mins -> onSnooze(reminder, mins) },
                        onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                        onClick = { onEditReminder(reminder) }
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
private fun DailyProgressHeroCard(
    completionFraction: Float,
    todayCompleted: Int,
    todayTotal: Int,
    overdueCount: Int,
    upcomingCount: Int,
    onOpenCreate: () -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )

    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Daily Flow Progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = if (todayTotal > 0) {
                            "$todayCompleted of $todayTotal today's tasks done"
                        } else {
                            "Ready to plan your day"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusStatPill(
                            label = "Overdue",
                            value = overdueCount.toString()
                        )
                        StatusStatPill(
                            label = "Upcoming",
                            value = upcomingCount.toString()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = onOpenCreate,
                        modifier = Modifier.testTag("hero_new_reminder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Schedule Reminder")
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(88.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White.copy(alpha = 0.22f),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { completionFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${(completionFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusStatPill(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun CompactTimelineChip(
    reminder: ReminderEntity,
    use24Hour: Boolean,
    onClick: () -> Unit
) {
    val accent = parseHexColor(reminder.colorHex)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .width(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${DateTimeUtils.formatFriendlyDate(reminder.localDateStr)} • ${
                        DateTimeUtils.formatDisplayTime(reminder.localTimeStr, use24Hour)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Surface(
            color = accentColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CategoryTagEntity
import com.example.data.DateTimeUtils
import com.example.data.DefaultCategoryTags
import com.example.data.ReminderEntity
import com.example.notifications.NotificationDiagnostics
import com.example.ui.components.CategoryFilterChipRow
import com.example.ui.components.ReminderCard
import com.example.ui.components.parseHexColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    reminders: List<ReminderEntity>,
    categoryTags: List<CategoryTagEntity> = DefaultCategoryTags.presetEntities(),
    selectedCategoryLabels: Set<String> = emptySet(),
    use24Hour: Boolean,
    diagnostics: NotificationDiagnostics?,
    onQuickAdd: (String) -> Unit,
    onOpenFullCreate: () -> Unit,
    onEditReminder: (ReminderEntity) -> Unit,
    onToggleComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity, Int) -> Unit,
    onTogglePauseRecurrence: (ReminderEntity) -> Unit,
    onToggleCategoryLabel: (String) -> Unit = {},
    onClearCategories: () -> Unit = {},
    onCreateCustomTag: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomTag: (String) -> Unit = {},
    onOpenDiagnostics: () -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val todayStr = DateTimeUtils.formatLocalDate(today)
    val nowMillis = System.currentTimeMillis()

    var showCompletedSection by remember { mutableStateOf(false) }

    val categoryFilteredReminders = remember(reminders, selectedCategoryLabels) {
        if (selectedCategoryLabels.isEmpty()) {
            reminders
        } else {
            reminders.filter { it.matchesCategoryFilter(selectedCategoryLabels) }
        }
    }

    val overdueTasks = remember(categoryFilteredReminders, nowMillis) {
        categoryFilteredReminders.filter { !it.isCompleted && it.effectiveTriggerMillis() < nowMillis }
            .sortedBy { it.effectiveTriggerMillis() }
    }

    val todayActiveTasks = remember(categoryFilteredReminders, todayStr, nowMillis) {
        categoryFilteredReminders.filter {
            it.localDateStr == todayStr && !it.isCompleted && it.effectiveTriggerMillis() >= nowMillis
        }.sortedBy { it.localTimeStr }
    }

    val upcomingTasks = remember(categoryFilteredReminders, todayStr) {
        categoryFilteredReminders.filter { it.localDateStr > todayStr && !it.isCompleted }
            .sortedWith(compareBy<ReminderEntity> { it.localDateStr }.thenBy { it.localTimeStr })
    }

    val completedTasks = remember(categoryFilteredReminders) {
        categoryFilteredReminders.filter { it.isCompleted }
            .sortedByDescending { it.updatedAtEpochMillis }
    }

    val todayAllCount = remember(reminders, todayStr) {
        reminders.count { it.localDateStr == todayStr }
    }
    val todayCompletedCount = remember(reminders, todayStr) {
        reminders.count { it.localDateStr == todayStr && it.isCompleted }
    }

    val completionFraction = if (todayAllCount > 0) {
        todayCompletedCount.toFloat() / todayAllCount.toFloat()
    } else {
        0f
    }

    val next24HoursLimitMillis = nowMillis + (24 * 60 * 60 * 1000L)
    val next24HoursEvents = remember(categoryFilteredReminders, nowMillis, next24HoursLimitMillis) {
        categoryFilteredReminders.filter {
            !it.isCompleted &&
                it.effectiveTriggerMillis() in nowMillis..next24HoursLimitMillis
        }.sortedBy { it.effectiveTriggerMillis() }
    }

    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Minimalist Material 3 Header & Outlined Progress Summary Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = today.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Outlined Reliability Status Chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (diagnostics?.isFullyOperational == true) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (diagnostics?.isFullyOperational == true) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            }
                        ),
                        modifier = Modifier
                            .clickable(onClick = onOpenDiagnostics)
                            .testTag("dashboard_diagnostics_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (diagnostics?.isFullyOperational == true) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (diagnostics?.isFullyOperational == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (diagnostics?.isFullyOperational == true) "Alerts Active" else "Setup Alerts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (diagnostics?.isFullyOperational == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Minimalist Outlined Progress Summary Card
                if (todayAllCount > 0) {
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Today's Focus Progress",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$todayCompletedCount / $todayAllCount (${(completionFraction * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { completionFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // Multi-Select Category Filter Bar with Task Counts
                CategoryFilterChipRow(
                    reminders = reminders,
                    categoryTags = categoryTags,
                    selectedCategoryLabels = selectedCategoryLabels,
                    onToggleCategoryLabel = onToggleCategoryLabel,
                    onClearCategories = onClearCategories,
                    onCreateCustomTag = onCreateCustomTag,
                    onDeleteCustomTag = onDeleteCustomTag
                )
            }
        }

        // 2. Compact Next 24h Outlined Timeline Strip
        if (next24HoursEvents.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Next 24 Hours",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(next24HoursEvents, key = { "timeline_${it.id}" }) { event ->
                            val catColor = parseHexColor(event.colorHex, MaterialTheme.colorScheme.primary)
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable { onEditReminder(event) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Text(
                                        text = DateTimeUtils.formatDisplayTime(event.localTimeStr, use24Hour),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Overdue Tasks
        if (overdueTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overdue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${overdueTasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            items(overdueTasks, key = { "overdue_${it.id}" }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    use24Hour = use24Hour,
                    nowMillis = nowMillis,
                    onToggleComplete = { onToggleComplete(reminder) },
                    onSnooze = { mins -> onSnooze(reminder, mins) },
                    onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                    onClick = { onEditReminder(reminder) },
                    onCategoryChipClick = { label -> onToggleCategoryLabel(label) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 200),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = tween(durationMillis = 180)
                    )
                )
            }
        }

        // 4. Today's Tasks
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${todayActiveTasks.size} remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (todayActiveTasks.isEmpty()) {
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (selectedCategoryLabels.isNotEmpty()) {
                                "No active tasks today for ${selectedCategoryLabels.joinToString(", ")}."
                            } else {
                                "All clear for today. Tap New Task to plan ahead."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedCategoryLabels.isNotEmpty()) {
                            TextButton(onClick = onClearCategories) {
                                Text("Show All", style = MaterialTheme.typography.labelSmall)
                            }
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
                    onClick = { onEditReminder(reminder) },
                    onCategoryChipClick = { label -> onToggleCategoryLabel(label) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 200),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = tween(durationMillis = 180)
                    )
                )
            }
        }

        // 5. Upcoming Schedule
        if (upcomingTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${upcomingTasks.size} tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(upcomingTasks.take(6), key = { "upcoming_${it.id}" }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    use24Hour = use24Hour,
                    nowMillis = nowMillis,
                    onToggleComplete = { onToggleComplete(reminder) },
                    onSnooze = { mins -> onSnooze(reminder, mins) },
                    onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                    onClick = { onEditReminder(reminder) },
                    onCategoryChipClick = { label -> onToggleCategoryLabel(label) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 200),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = tween(durationMillis = 180)
                    )
                )
            }
        }

        // 6. Completed Tasks (Collapsible)
        if (completedTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCompletedSection = !showCompletedSection },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Completed (${completedTasks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showCompletedSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(onClick = onClearCompleted) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showCompletedSection) {
                items(completedTasks.take(5), key = { "completed_${it.id}" }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        use24Hour = use24Hour,
                        nowMillis = nowMillis,
                        onToggleComplete = { onToggleComplete(reminder) },
                        onSnooze = { mins -> onSnooze(reminder, mins) },
                        onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                        onClick = { onEditReminder(reminder) },
                        onCategoryChipClick = { label -> onToggleCategoryLabel(label) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 200),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = tween(durationMillis = 180)
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

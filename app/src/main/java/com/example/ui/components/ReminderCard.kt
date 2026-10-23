package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import com.example.ui.theme.CompletedSuccessColor
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.SnoozeAccentColor
import kotlinx.coroutines.delay

fun parseHexColor(hex: String, fallback: Color = PriorityLowColor): Color {
    return try {
        val clean = hex.removePrefix("#")
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or longVal)
        } else {
            Color(longVal)
        }
    } catch (_: Exception) {
        fallback
    }
}

fun priorityColor(priority: ReminderPriority): Color = when (priority) {
    ReminderPriority.HIGH -> PriorityHighColor
    ReminderPriority.MEDIUM -> PriorityMediumColor
    ReminderPriority.LOW -> PriorityLowColor
}

/**
 * Lightweight, border-minimal task item with clean single-line metadata.
 * Strips heavy color bars and stacked pills for optimal scannability and fast rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCard(
    reminder: ReminderEntity,
    use24Hour: Boolean,
    nowMillis: Long,
    onToggleComplete: () -> Unit,
    onSnooze: (Int) -> Unit,
    onTogglePauseRecurrence: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSnoozeMenu by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var isCompletingAnim by remember(reminder.id, reminder.isCompleted) { mutableStateOf(false) }
    var pendingCompletionCommit by remember(reminder.id) { mutableStateOf(false) }
    val currentOnToggleComplete by rememberUpdatedState(onToggleComplete)

    val triggerCompleteWithFeedback = {
        if (!reminder.isCompleted) {
            if (!isCompletingAnim) {
                isCompletingAnim = true
                pendingCompletionCommit = true
            }
        } else {
            currentOnToggleComplete()
        }
    }

    LaunchedEffect(isCompletingAnim) {
        if (isCompletingAnim) {
            delay(240L)
            if (pendingCompletionCommit) {
                pendingCompletionCommit = false
                isCompletingAnim = false
                currentOnToggleComplete()
            }
        }
    }

    DisposableEffect(reminder.id) {
        onDispose {
            if (pendingCompletionCommit) {
                pendingCompletionCommit = false
                currentOnToggleComplete()
            }
        }
    }

    val visualCompleted = reminder.isCompleted || isCompletingAnim
    val isOverdue = !visualCompleted && reminder.effectiveTriggerMillis() < nowMillis
    val prioColor = priorityColor(reminder.priority)

    val containerColor by animateColorAsState(
        targetValue = when {
            visualCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "containerColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            visualCompleted -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )

    val titleTextColor by animateColorAsState(
        targetValue = if (visualCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 200),
        label = "titleTextColor"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (visualCompleted) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )

    val strikethroughProgress by animateFloatAsState(
        targetValue = if (visualCompleted) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "strikethroughProgress"
    )

    val slideOutProgress by animateFloatAsState(
        targetValue = if (isCompletingAnim) 1f else 0f,
        animationSpec = if (isCompletingAnim) {
            tween(durationMillis = 180, delayMillis = 60, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "slideOutProgress"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            when (targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    triggerCompleteWithFeedback()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!visualCompleted) {
                        onSnooze(10)
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = !visualCompleted,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> CompletedSuccessColor.copy(alpha = 0.16f)
                    SwipeToDismissBoxValue.EndToStart -> SnoozeAccentColor.copy(alpha = 0.16f)
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "swipeBgColor"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CompletedSuccessColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (reminder.isCompleted) "Reopen" else "Complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = CompletedSuccessColor
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Snooze 10m",
                            style = MaterialTheme.typography.labelMedium,
                            color = SnoozeAccentColor
                        )
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = SnoozeAccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = slideOutProgress * 48.dp.toPx()
                alpha = 1f - (slideOutProgress * 0.7f)
                val scale = 1f - (slideOutProgress * 0.02f)
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reminder_card_${reminder.id}")
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accessible Checkbox
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .testTag("complete_checkbox_${reminder.id}")
                        .clickable(onClick = triggerCompleteWithFeedback),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .scale(checkScale)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (visualCompleted) CompletedSuccessColor else Color.Transparent
                            )
                            .border(
                                width = 1.75.dp,
                                color = if (visualCompleted) CompletedSuccessColor else prioColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (visualCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Content Column: Title + Single-Line Metadata
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (visualCompleted) FontWeight.Normal else FontWeight.Medium,
                        color = titleTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                            .testTag("reminder_title_${reminder.id}")
                            .drawWithContent {
                                drawContent()
                                if (strikethroughProgress > 0.01f) {
                                    val layout = textLayoutResult
                                    val lineWidth = if (layout != null && layout.lineCount > 0) {
                                        layout.getLineRight(0).coerceAtMost(size.width)
                                    } else {
                                        size.width
                                    }
                                    val centerY = size.height * 0.53f
                                    drawLine(
                                        color = titleTextColor.copy(alpha = 0.8f),
                                        start = Offset(0f, centerY),
                                        end = Offset(lineWidth * strikethroughProgress, centerY),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                    )

                    // Single-line compact metadata subtitle: [Alarm] 10:30 AM • Work • 🔁
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val displayTime = DateTimeUtils.formatDisplayTime(reminder.localTimeStr, use24Hour)
                        val friendlyDate = DateTimeUtils.formatFriendlyDate(reminder.localDateStr)

                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isOverdue) "Overdue • $friendlyDate, $displayTime" else "$friendlyDate, $displayTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Text(
                            text = reminder.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (reminder.isRecurring) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Recurring",
                                tint = if (reminder.isRecurrencePaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        val snoozedUntil = reminder.snoozedUntilEpochMillis
                        if (snoozedUntil != null && snoozedUntil > nowMillis && !visualCompleted) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = "Snoozed",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnoozeAccentColor
                            )
                        }

                        if (!reminder.notificationEnabled) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Muted",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                // Priority Badge (only if High or Medium to avoid visual clutter)
                if (reminder.priority != ReminderPriority.LOW) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = prioColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = reminder.priority.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = prioColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Quick Snooze Dropdown
                if (!visualCompleted) {
                    Box {
                        IconButton(
                            onClick = { showSnoozeMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("snooze_button_${reminder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = "Snooze",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSnoozeMenu,
                            onDismissRequest = { showSnoozeMenu = false }
                        ) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("Snooze $mins min") },
                                    onClick = {
                                        showSnoozeMenu = false
                                        onSnooze(mins)
                                    },
                                    modifier = Modifier.testTag("snooze_option_${mins}m")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import com.example.ui.theme.CompletedSuccessColor
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor

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

@OptIn(ExperimentalLayoutApi::class)
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
    val isOverdue = !reminder.isCompleted && reminder.effectiveTriggerMillis() < nowMillis
    val categoryColor = remember(reminder.colorHex) {
        parseHexColor(reminder.colorHex)
    }
    val prioColor = priorityColor(reminder.priority)

    val containerColor by animateColorAsState(
        targetValue = when {
            reminder.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "cardContainerColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (reminder.isCompleted) 0.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left vertical category/priority accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(if (reminder.isCompleted) CompletedSuccessColor else categoryColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Custom accessible 48dp touch target completion button
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .testTag("complete_checkbox_${reminder.id}")
                        .clickable(onClick = onToggleComplete),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (reminder.isCompleted) CompletedSuccessColor else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (reminder.isCompleted) CompletedSuccessColor else prioColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (reminder.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (reminder.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (reminder.isCompleted) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Priority pill
                        Surface(
                            color = prioColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = reminder.priority.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = prioColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (reminder.description.isNotBlank()) {
                        Text(
                            text = reminder.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata badges row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Date & Time chip
                        val friendlyDate = DateTimeUtils.formatFriendlyDate(reminder.localDateStr)
                        val displayTime = DateTimeUtils.formatDisplayTime(reminder.localTimeStr, use24Hour)
                        Surface(
                            color = if (isOverdue) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = "Scheduled time",
                                    modifier = Modifier.size(13.dp),
                                    tint = if (isOverdue) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Text(
                                    text = if (isOverdue) {
                                        "Overdue • $friendlyDate, $displayTime"
                                    } else {
                                        "$friendlyDate, $displayTime"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isOverdue) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        // Category chip
                        Surface(
                            color = categoryColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = reminder.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Recurrence badge
                        if (reminder.isRecurring) {
                            Surface(
                                color = if (reminder.isRecurrencePaused) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Recurring",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = DateTimeUtils.formatRecurrenceSummary(reminder),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Snoozed badge
                        val snoozedUntil = reminder.snoozedUntilEpochMillis
                        if (snoozedUntil != null && snoozedUntil > nowMillis && !reminder.isCompleted) {
                            val snoozeTimeStr = DateTimeUtils.formatEpochTime(snoozedUntil, use24Hour)
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Snoozed until $snoozeTimeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Notification status icon
                        Icon(
                            imageVector = if (reminder.notificationEnabled) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsOff
                            },
                            contentDescription = if (reminder.notificationEnabled) {
                                "Notifications enabled"
                            } else {
                                "Notifications muted"
                            },
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.CenterVertically),
                            tint = if (reminder.notificationEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }

                // Quick actions column (Snooze + Pause/Resume if recurring)
                if (!reminder.isCompleted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            IconButton(
                                onClick = { showSnoozeMenu = true },
                                modifier = Modifier.testTag("snooze_button_${reminder.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Snooze,
                                    contentDescription = "Snooze reminder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

                        if (reminder.isRecurring) {
                            IconButton(
                                onClick = onTogglePauseRecurrence,
                                modifier = Modifier.testTag("pause_recurrence_${reminder.id}")
                            ) {
                                Icon(
                                    imageVector = if (reminder.isRecurrencePaused) {
                                        Icons.Default.PlayCircleOutline
                                    } else {
                                        Icons.Default.PauseCircleOutline
                                    },
                                    contentDescription = if (reminder.isRecurrencePaused) {
                                        "Resume recurring schedule"
                                    } else {
                                        "Pause recurring schedule"
                                    },
                                    tint = if (reminder.isRecurrencePaused) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

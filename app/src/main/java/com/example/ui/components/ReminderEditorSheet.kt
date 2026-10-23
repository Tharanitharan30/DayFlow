package com.example.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.RecurrenceType
import com.example.data.RecurringEditScope
import com.example.data.ReminderCategory
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val AdvanceAlertOptions = listOf(
    0 to "On time",
    5 to "5m before",
    10 to "10m before",
    15 to "15m before",
    30 to "30m before",
    60 to "1h before",
    1440 to "1d before"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorSheet(
    initialReminder: ReminderEntity,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity, RecurringEditScope) -> Unit,
    onDelete: (ReminderEntity, Boolean) -> Unit
) {
    val isEditingExisting = initialReminder.id != 0L
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(initialReminder) { mutableStateOf(initialReminder.title) }
    var description by remember(initialReminder) { mutableStateOf(initialReminder.description) }
    var localDateStr by remember(initialReminder) { mutableStateOf(initialReminder.localDateStr) }
    var localTimeStr by remember(initialReminder) { mutableStateOf(initialReminder.localTimeStr) }
    var category by remember(initialReminder) { mutableStateOf(initialReminder.category) }
    var priority by remember(initialReminder) { mutableStateOf(initialReminder.priority) }
    var recurrenceType by remember(initialReminder) { mutableStateOf(initialReminder.recurrenceType) }
    var recurrenceInterval by remember(initialReminder) {
        mutableIntStateOf(initialReminder.recurrenceInterval.coerceAtLeast(1))
    }
    var selectedWeekdays by remember(initialReminder) {
        mutableStateOf(
            initialReminder.parsedWeekdays().ifEmpty {
                setOf(DateTimeUtils.parseLocalDate(initialReminder.localDateStr).dayOfWeek.value)
            }
        )
    }
    var recurrenceEndDate by remember(initialReminder) {
        mutableStateOf(initialReminder.recurrenceEndDate ?: "")
    }
    var recurrenceMaxOccurrencesText by remember(initialReminder) {
        mutableStateOf(initialReminder.recurrenceMaxOccurrences?.toString() ?: "")
    }
    var isRecurrencePaused by remember(initialReminder) {
        mutableStateOf(initialReminder.isRecurrencePaused)
    }
    var notificationEnabled by remember(initialReminder) {
        mutableStateOf(initialReminder.notificationEnabled)
    }
    var isRingingAlarm by remember(initialReminder) {
        mutableStateOf(initialReminder.isRingingAlarm)
    }
    var alarmVibration by remember(initialReminder) {
        mutableStateOf(initialReminder.alarmVibration)
    }
    var snoozeDurationMinutes by remember(initialReminder) {
        mutableIntStateOf(initialReminder.snoozeDurationMinutes)
    }
    var selectedAdvanceOffsets by remember(initialReminder) {
        mutableStateOf(initialReminder.parsedAdvanceOffsets().toSet())
    }
    var editScope by remember(initialReminder) {
        mutableStateOf(RecurringEditScope.THIS_OCCURRENCE_ONLY)
    }

    // Audio Preview Player
    val context = LocalContext.current
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) {}
            previewPlayer = null
            isPreviewPlaying = false
        }
    }

    // Lightweight Accordion States
    var isRecurrenceExpanded by remember { mutableStateOf(initialReminder.isRecurring) }
    var isAlertsExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        text = if (isEditingExisting) "Edit Task" else "New Task",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isEditingExisting) {
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("editor_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Task",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 1. Task Title & Notes
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title *") },
                placeholder = { Text("What do you want to accomplish?") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Add extra details...") },
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_description_input")
            )

            // 2. Primary Date & Time Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_date_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatFriendlyDate(localDateStr),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_time_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatDisplayTime(localTimeStr, use24Hour),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 3. Priority Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderPriority.entries.forEach { prio ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(prio.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_priority_${prio.name.lowercase()}")
                        )
                    }
                }
            }

            // 4. Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReminderCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("editor_cat_${cat.name.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider()

            // 5. Expandable Accordion: Repeat Schedule
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isRecurrenceExpanded = !isRecurrenceExpanded }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (recurrenceType != RecurrenceType.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Repeat Schedule",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = recurrenceType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isRecurrenceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isRecurrenceExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceType.entries.forEach { rType ->
                            FilterChip(
                                selected = recurrenceType == rType,
                                onClick = { recurrenceType = rType },
                                label = { Text(rType.label) },
                                modifier = Modifier.testTag("editor_recurrence_${rType.name.lowercase()}")
                            )
                        }
                    }

                    if (recurrenceType == RecurrenceType.SELECTED_WEEKDAYS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("M" to 1, "T" to 2, "W" to 3, "T" to 4, "F" to 5, "S" to 6, "S" to 7).forEach { (dayLabel, dayVal) ->
                                val isSelected = selectedWeekdays.contains(dayVal)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedWeekdays = if (isSelected) {
                                            if (selectedWeekdays.size > 1) selectedWeekdays - dayVal else selectedWeekdays
                                        } else {
                                            selectedWeekdays + dayVal
                                        }
                                    },
                                    label = { Text(dayLabel) }
                                )
                            }
                        }
                    }

                    if (isEditingExisting && recurrenceType != RecurrenceType.NONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pause recurring series", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = isRecurrencePaused,
                                onCheckedChange = { isRecurrencePaused = it }
                            )
                        }
                    }
                }
            }

            // 6. Expandable Accordion: Notifications & Advance Alerts
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAlertsExpanded = !isAlertsExpanded }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                            tint = if (notificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Notifications & Advance Alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (notificationEnabled) "${selectedAdvanceOffsets.size} alert(s) enabled" else "Muted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isAlertsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isAlertsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Notification Alerts", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = notificationEnabled,
                            onCheckedChange = { notificationEnabled = it },
                            modifier = Modifier.testTag("editor_notif_switch")
                        )
                    }

                    // Ringing Clock Alarm Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (isRingingAlarm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Ringing Clock Alarm", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Plays loud ringtone & shows full-screen alarm over lock screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRingingAlarm,
                            onCheckedChange = { isRingingAlarm = it },
                            modifier = Modifier.testTag("editor_ringing_alarm_switch")
                        )
                    }

                    // Sound Preview & Snooze Controls (when Ringing Alarm enabled)
                    if (isRingingAlarm) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Alarm Sound", style = MaterialTheme.typography.labelLarge)
                                        Text("Default Android Ringtone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (isPreviewPlaying) {
                                                previewPlayer?.let {
                                                    if (it.isPlaying) it.stop()
                                                    it.release()
                                                }
                                                previewPlayer = null
                                                isPreviewPlaying = false
                                            } else {
                                                try {
                                                    val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                                                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                                    previewPlayer = MediaPlayer().apply {
                                                        setAudioAttributes(
                                                            AudioAttributes.Builder()
                                                                .setUsage(AudioAttributes.USAGE_ALARM)
                                                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                                                .build()
                                                        )
                                                        setDataSource(context, alarmUri)
                                                        prepare()
                                                        start()
                                                    }
                                                    isPreviewPlaying = true
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        modifier = Modifier.testTag("preview_alarm_sound_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPreviewPlaying) "Stop" else "Preview")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Snooze Duration", style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(5, 10, 15).forEach { mins ->
                                            FilterChip(
                                                selected = snoozeDurationMinutes == mins,
                                                onClick = { snoozeDurationMinutes = mins },
                                                label = { Text("$mins m") },
                                                modifier = Modifier.testTag("snooze_chip_${mins}m")
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Vibrate during alarm", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = alarmVibration,
                                        onCheckedChange = { alarmVibration = it }
                                    )
                                }
                            }
                        }
                    }

                    if (notificationEnabled) {
                        Text(
                            text = "Remind me in advance:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdvanceAlertOptions.forEach { (offsetMins, label) ->
                                val isSelected = selectedAdvanceOffsets.contains(offsetMins)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedAdvanceOffsets = if (isSelected) {
                                            if (selectedAdvanceOffsets.size > 1) selectedAdvanceOffsets - offsetMins else selectedAdvanceOffsets
                                        } else {
                                            selectedAdvanceOffsets + offsetMins
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Recurring Edit Scope Option (if editing an existing recurring series)
            if (isEditingExisting && initialReminder.isRecurring) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Apply edits to:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = editScope == RecurringEditScope.THIS_OCCURRENCE_ONLY,
                            onClick = { editScope = RecurringEditScope.THIS_OCCURRENCE_ONLY },
                            label = { Text("This task only") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = editScope == RecurringEditScope.ENTIRE_SERIES,
                            onClick = { editScope = RecurringEditScope.ENTIRE_SERIES },
                            label = { Text("Entire series") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Save Action Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val computedEpoch = DateTimeUtils.computeEpochMillis(localDateStr, localTimeStr)
                        val advanceStr = selectedAdvanceOffsets.sorted().joinToString(",")
                        val weekdaysStr = selectedWeekdays.sorted().joinToString(",")
                        val maxOccur = recurrenceMaxOccurrencesText.toIntOrNull()

                        val updated = initialReminder.copy(
                            title = title.trim(),
                            description = description.trim(),
                            localDateStr = localDateStr,
                            localTimeStr = localTimeStr,
                            scheduledEpochMillis = computedEpoch,
                            category = category,
                            priority = priority,
                            recurrenceType = recurrenceType,
                            recurrenceInterval = recurrenceInterval,
                            recurrenceWeekdays = weekdaysStr,
                            recurrenceEndDate = recurrenceEndDate.ifBlank { null },
                            recurrenceMaxOccurrences = maxOccur,
                            isRecurrencePaused = isRecurrencePaused,
                            notificationEnabled = notificationEnabled,
                            isRingingAlarm = isRingingAlarm,
                            alarmVibration = alarmVibration,
                            snoozeDurationMinutes = snoozeDurationMinutes,
                            advanceAlertOffsetsMinutes = advanceStr
                        )
                        onSave(updated, editScope)
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("editor_save_button")
            ) {
                Text(if (isEditingExisting) "Save Changes" else "Create Task")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Material 3 DatePickerDialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateTimeUtils.parseLocalDate(localDateStr)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedEpoch = datePickerState.selectedDateMillis
                        if (selectedEpoch != null) {
                            val pickedDate = Instant.ofEpochMilli(selectedEpoch)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            localDateStr = DateTimeUtils.formatLocalDate(pickedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 TimePickerDialog
    if (showTimePicker) {
        val parsedTime = DateTimeUtils.parseLocalTime(localTimeStr)
        val timePickerState = rememberTimePickerState(
            initialHour = parsedTime.hour,
            initialMinute = parsedTime.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        localTimeStr = String.format(java.util.Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Task") },
            text = {
                Text(
                    if (initialReminder.isRecurring) {
                        "Do you want to delete only this occurrence or the entire recurring series?"
                    } else {
                        "Are you sure you want to delete this task? This action cannot be undone."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(initialReminder, false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (initialReminder.isRecurring) "Delete This Task" else "Delete")
                }
            },
            dismissButton = {
                if (initialReminder.isRecurring) {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete(initialReminder, true)
                        }
                    ) {
                        Text("Delete Entire Series", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

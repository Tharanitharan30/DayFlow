package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private val ColorPaletteHex = listOf(
    "#0A6670", // Deep Teal
    "#4338CA", // Indigo
    "#10B981", // Emerald
    "#D97706", // Amber
    "#EC4899", // Pink
    "#8B5CF6", // Violet
    "#E11D48", // Rose Red
    "#0284C7"  // Sky Blue
)

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
    var colorHex by remember(initialReminder) { mutableStateOf(initialReminder.colorHex) }
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
    var selectedAdvanceOffsets by remember(initialReminder) {
        mutableStateOf(initialReminder.parsedAdvanceOffsets().toSet())
    }
    var editScope by remember(initialReminder) {
        mutableStateOf(RecurringEditScope.THIS_OCCURRENCE_ONLY)
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditingExisting) "Edit Reminder" else "New Reminder",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (isEditingExisting) {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("editor_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete reminder",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                placeholder = { Text("e.g., Take vitamin D, Submit project report") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes / Description (optional)") },
                placeholder = { Text("Add checklist items, links, or context...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_description_input")
            )

            // Date & Time Selection Section
            Text(
                text = "Date & Scheduled Time",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_date_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Pick Date",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(DateTimeUtils.formatFriendlyDate(localDateStr))
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_time_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Pick Time",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(DateTimeUtils.formatDisplayTime(localTimeStr, use24Hour))
                }
            }

            // Quick Date & Time Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val today = LocalDate.now()
                listOf(
                    "Today" to DateTimeUtils.formatLocalDate(today),
                    "Tomorrow" to DateTimeUtils.formatLocalDate(today.plusDays(1)),
                    "In 3 Days" to DateTimeUtils.formatLocalDate(today.plusDays(3)),
                    "Next Week" to DateTimeUtils.formatLocalDate(today.plusWeeks(1))
                ).forEach { (label, dateVal) ->
                    FilterChip(
                        selected = localDateStr == dateVal,
                        onClick = { localDateStr = dateVal },
                        label = { Text(label) }
                    )
                }
                listOf("09:00", "12:30", "17:00", "20:00").forEach { presetTime ->
                    FilterChip(
                        selected = localTimeStr == presetTime,
                        onClick = { localTimeStr = presetTime },
                        label = { Text(DateTimeUtils.formatDisplayTime(presetTime, use24Hour)) }
                    )
                }
            }

            HorizontalDivider()

            // Priority Selection
            Text(
                text = "Priority Level",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReminderPriority.entries.forEach { prio ->
                    val selected = priority == prio
                    FilterChip(
                        selected = selected,
                        onClick = { priority = prio },
                        label = { Text(prio.label) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("priority_chip_${prio.name.lowercase()}")
                    )
                }
            }

            // Category & Color Swatch
            Text(
                text = "Category & Accent Color",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = {
                            category = cat
                            colorHex = cat.defaultColorHex
                        },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("category_chip_${cat.name.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ColorPaletteHex.forEach { hex ->
                    val swatchColor = parseHexColor(hex)
                    val isSelected = colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                     Color.Transparent
                                },
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected color",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Recurrence Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Recurrence Schedule",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurrenceType.entries.forEach { type ->
                    FilterChip(
                        selected = recurrenceType == type,
                        onClick = { recurrenceType = type },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag("recurrence_chip_${type.name.lowercase()}")
                    )
                }
            }

            if (recurrenceType == RecurrenceType.SELECTED_WEEKDAYS) {
                val dayLabels = listOf(
                    1 to "Mon",
                    2 to "Tue",
                    3 to "Wed",
                    4 to "Thu",
                    5 to "Fri",
                    6 to "Sat",
                    7 to "Sun"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayLabels.forEach { (dayNum, label) ->
                        val isDaySelected = dayNum in selectedWeekdays
                        FilterChip(
                            selected = isDaySelected,
                            onClick = {
                                selectedWeekdays = if (isDaySelected && selectedWeekdays.size > 1) {
                                    selectedWeekdays - dayNum
                                } else {
                                    selectedWeekdays + dayNum
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (recurrenceType in setOf(
                    RecurrenceType.CUSTOM_DAYS,
                    RecurrenceType.WEEKLY,
                    RecurrenceType.MONTHLY
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Repeat every:", style = MaterialTheme.typography.bodyMedium)
                    listOf(1, 2, 3, 5, 7, 14).forEach { intervalVal ->
                        FilterChip(
                            selected = recurrenceInterval == intervalVal,
                            onClick = { recurrenceInterval = intervalVal },
                            label = { Text("$intervalVal") }
                        )
                    }
                }
            }

            if (recurrenceType != RecurrenceType.NONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = recurrenceEndDate,
                        onValueChange = { recurrenceEndDate = it.trim() },
                        label = { Text("End Date (YYYY-MM-DD)") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = recurrenceMaxOccurrencesText,
                        onValueChange = {
                            recurrenceMaxOccurrencesText = it.filter { ch -> ch.isDigit() }
                        },
                        label = { Text("Max Occurrences") },
                        placeholder = { Text("Unlimited") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pause Recurring Schedule", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Temporarily stop notifications and auto-advancement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRecurrencePaused,
                        onCheckedChange = { isRecurrencePaused = it }
                    )
                }
            }

            HorizontalDivider()

            // Notifications & Multiple Advance Alerts
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
                            text = "Native Android Notification",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Fires via AlarmManager even when app is closed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = { notificationEnabled = it },
                    modifier = Modifier.testTag("editor_notification_switch")
                )
            }

            if (notificationEnabled) {
                Text(
                    text = "Alert Timing (select one or multiple):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AdvanceAlertOptions.forEach { (offsetMin, label) ->
                        val isSelected = offsetMin in selectedAdvanceOffsets
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedAdvanceOffsets = if (isSelected && selectedAdvanceOffsets.size > 1) {
                                    selectedAdvanceOffsets - offsetMin
                                } else {
                                    selectedAdvanceOffsets + offsetMin
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // If editing an existing recurring reminder, choose occurrence vs series scope
            if (isEditingExisting && initialReminder.isRecurring) {
                HorizontalDivider()
                Text(
                    text = "Apply Changes To:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = editScope == RecurringEditScope.THIS_OCCURRENCE_ONLY,
                        onClick = { editScope = RecurringEditScope.THIS_OCCURRENCE_ONLY },
                        label = { Text("This Occurrence Only") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = editScope == RecurringEditScope.ENTIRE_SERIES,
                        onClick = { editScope = RecurringEditScope.ENTIRE_SERIES },
                        label = { Text("Entire Series") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_cancel_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val updatedEntity = initialReminder.copy(
                            title = title.trim(),
                            description = description.trim(),
                            localDateStr = localDateStr,
                            localTimeStr = localTimeStr,
                            category = category,
                            colorHex = colorHex,
                            priority = priority,
                            recurrenceType = recurrenceType,
                            recurrenceInterval = recurrenceInterval.coerceAtLeast(1),
                            recurrenceWeekdays = selectedWeekdays.sorted().joinToString(","),
                            recurrenceEndDate = recurrenceEndDate.takeIf { it.isNotBlank() },
                            recurrenceMaxOccurrences = recurrenceMaxOccurrencesText.toIntOrNull(),
                            isRecurrencePaused = isRecurrencePaused,
                            notificationEnabled = notificationEnabled,
                            advanceAlertOffsetsMinutes = selectedAdvanceOffsets.sorted().joinToString(",")
                        )
                        onSave(updatedEntity, editScope)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_save_button")
                ) {
                    Text(if (isEditingExisting) "Save Changes" else "Create Reminder")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDatePicker) {
        val currentLocalDate = DateTimeUtils.parseLocalDate(localDateStr)
        val initialMillis = currentLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            localDateStr = DateTimeUtils.formatLocalDate(picked)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm")
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

    if (showTimePicker) {
        val currentLocalTime = DateTimeUtils.parseLocalTime(localTimeStr)
        val timePickerState = rememberTimePickerState(
            initialHour = currentLocalTime.hour,
            initialMinute = currentLocalTime.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Reminder Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        localTimeStr = DateTimeUtils.formatLocalTime(pickedTime)
                        showTimePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Reminder?") },
            text = {
                Text(
                    if (initialReminder.isRecurring) {
                        "This is a recurring reminder. Would you like to delete only this occurrence or the entire recurring series?"
                    } else {
                        "Are you sure you want to permanently delete \"${initialReminder.title}\"? Any scheduled notifications will be cancelled."
                    }
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (initialReminder.isRecurring) {
                        TextButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                onDelete(initialReminder, true)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Entire Series")
                        }
                    }
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete(initialReminder, false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(if (initialReminder.isRecurring) "This Occurrence" else "Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

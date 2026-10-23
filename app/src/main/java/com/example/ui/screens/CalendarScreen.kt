package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.ReminderEntity
import com.example.ui.CalendarViewMode
import com.example.ui.components.ReminderCard
import com.example.ui.components.parseHexColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    reminders: List<ReminderEntity>,
    selectedDateStr: String,
    viewMode: CalendarViewMode,
    use24Hour: Boolean,
    weekStartsOnMonday: Boolean,
    onSelectDate: (String) -> Unit,
    onChangeViewMode: (CalendarViewMode) -> Unit,
    onOpenCreateForDate: (String) -> Unit,
    onEditReminder: (ReminderEntity) -> Unit,
    onToggleComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity, Int) -> Unit,
    onTogglePauseRecurrence: (ReminderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDate = remember(selectedDateStr) {
        DateTimeUtils.parseLocalDate(selectedDateStr)
    }
    val today = LocalDate.now()
    val nowMillis = System.currentTimeMillis()
    val monthHeaderFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US) }

    val remindersByDate = remember(reminders) {
        reminders.groupBy { it.localDateStr }
    }

    val selectedDayReminders = remember(reminders, selectedDateStr) {
        reminders.filter { it.localDateStr == selectedDateStr }
            .sortedWith(compareBy<ReminderEntity> { it.isCompleted }.thenBy { it.localTimeStr })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & View Mode Switcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDate.format(monthHeaderFormatter),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onSelectDate(DateTimeUtils.formatLocalDate(today)) },
                            modifier = Modifier.testTag("calendar_today_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = "Jump to Today",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Today")
                        }
                        IconButton(
                            onClick = {
                                val prev = when (viewMode) {
                                    CalendarViewMode.MONTHLY -> selectedDate.minusMonths(1)
                                    CalendarViewMode.WEEKLY -> selectedDate.minusWeeks(1)
                                    CalendarViewMode.DAILY, CalendarViewMode.AGENDA -> selectedDate.minusDays(1)
                                }
                                onSelectDate(DateTimeUtils.formatLocalDate(prev))
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                        }
                        IconButton(
                            onClick = {
                                val next = when (viewMode) {
                                    CalendarViewMode.MONTHLY -> selectedDate.plusMonths(1)
                                    CalendarViewMode.WEEKLY -> selectedDate.plusWeeks(1)
                                    CalendarViewMode.DAILY, CalendarViewMode.AGENDA -> selectedDate.plusDays(1)
                                }
                                onSelectDate(DateTimeUtils.formatLocalDate(next))
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                    }
                }

                // Mode Switcher Chips (Month / Week / Day / Agenda)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalendarViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { onChangeViewMode(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("calendar_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        when (viewMode) {
            CalendarViewMode.MONTHLY -> {
                item {
                    MonthCalendarGrid(
                        selectedDate = selectedDate,
                        today = today,
                        weekStartsOnMonday = weekStartsOnMonday,
                        remindersByDate = remindersByDate,
                        onSelectDate = onSelectDate
                    )
                }
            }

            CalendarViewMode.WEEKLY -> {
                item {
                    WeekStripCard(
                        selectedDate = selectedDate,
                        today = today,
                        weekStartsOnMonday = weekStartsOnMonday,
                        remindersByDate = remindersByDate,
                        onSelectDate = onSelectDate
                    )
                }
            }

            CalendarViewMode.DAILY -> {
                item {
                    WeekStripCard(
                        selectedDate = selectedDate,
                        today = today,
                        weekStartsOnMonday = weekStartsOnMonday,
                        remindersByDate = remindersByDate,
                        onSelectDate = onSelectDate
                    )
                }
                item {
                    DailyHourlyTimelineCard(
                        selectedDateStr = selectedDateStr,
                        dayReminders = selectedDayReminders,
                        use24Hour = use24Hour,
                        onEditReminder = onEditReminder,
                        onCreateAtDate = { onOpenCreateForDate(selectedDateStr) }
                    )
                }
            }

            CalendarViewMode.AGENDA -> {
                val groupedDates = remindersByDate.keys.sorted()
                if (groupedDates.isEmpty()) {
                    item {
                        EmptyCalendarDayCard(
                            dateLabel = "Your Agenda",
                            onAddClick = { onOpenCreateForDate(selectedDateStr) }
                        )
                    }
                } else {
                    groupedDates.forEach { dateKey ->
                        val itemsForDate = remindersByDate[dateKey].orEmpty()
                            .sortedBy { it.localTimeStr }
                        item(key = "agenda_header_$dateKey") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${DateTimeUtils.formatFriendlyDate(dateKey)} ($dateKey)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${itemsForDate.size} task(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        items(itemsForDate, key = { "agenda_item_${it.id}" }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                use24Hour = use24Hour,
                                nowMillis = nowMillis,
                                onToggleComplete = { onToggleComplete(reminder) },
                                onSnooze = { mins -> onSnooze(reminder, mins) },
                                onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                                onClick = { onEditReminder(reminder) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(durationMillis = 220),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = tween(durationMillis = 200)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Selected Day Tasks list for Month & Week views
        if (viewMode != CalendarViewMode.AGENDA) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = DateTimeUtils.formatFullHeaderDate(selectedDate),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${selectedDayReminders.size} scheduled reminder(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenCreateForDate(selectedDateStr) },
                        modifier = Modifier.testTag("calendar_add_on_date_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Here")
                    }
                }
            }

            if (selectedDayReminders.isEmpty()) {
                item {
                    EmptyCalendarDayCard(
                        dateLabel = DateTimeUtils.formatFriendlyDate(selectedDateStr),
                        onAddClick = { onOpenCreateForDate(selectedDateStr) }
                    )
                }
            } else {
                items(selectedDayReminders, key = { "cal_day_${it.id}" }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        use24Hour = use24Hour,
                        nowMillis = nowMillis,
                        onToggleComplete = { onToggleComplete(reminder) },
                        onSnooze = { mins -> onSnooze(reminder, mins) },
                        onTogglePauseRecurrence = { onTogglePauseRecurrence(reminder) },
                        onClick = { onEditReminder(reminder) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 220),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = tween(durationMillis = 200)
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

@Composable
private fun MonthCalendarGrid(
    selectedDate: LocalDate,
    today: LocalDate,
    weekStartsOnMonday: Boolean,
    remindersByDate: Map<String, List<ReminderEntity>>,
    onSelectDate: (String) -> Unit
) {
    val yearMonth = YearMonth.from(selectedDate)
    val firstOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()

    val dayHeaders = if (weekStartsOnMonday) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    } else {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    }

    val leadingEmptyCount = if (weekStartsOnMonday) {
        firstOfMonth.dayOfWeek.value - 1
    } else {
        firstOfMonth.dayOfWeek.value % 7
    }

    val totalCells = ((leadingEmptyCount + daysInMonth + 6) / 7) * 7

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                dayHeaders.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            for (weekRow in 0 until (totalCells / 7)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = weekRow * 7 + col
                        val dayNumber = cellIndex - leadingEmptyCount + 1
                        if (dayNumber in 1..daysInMonth) {
                            val cellDate = yearMonth.atDay(dayNumber)
                            val cellDateStr = DateTimeUtils.formatLocalDate(cellDate)
                            val isSelected = cellDate == selectedDate
                            val isToday = cellDate == today
                            val dayEvents = remindersByDate[cellDateStr].orEmpty()

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectDate(cellDateStr) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (dayEvents.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            dayEvents.take(3).forEach { ev ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) {
                                                                MaterialTheme.colorScheme.onPrimary
                                                            } else {
                                                                parseHexColor(ev.colorHex)
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStripCard(
    selectedDate: LocalDate,
    today: LocalDate,
    weekStartsOnMonday: Boolean,
    remindersByDate: Map<String, List<ReminderEntity>>,
    onSelectDate: (String) -> Unit
) {
    val startOfWeek = remember(selectedDate, weekStartsOnMonday) {
        val currentDow = selectedDate.dayOfWeek
        val offset = if (weekStartsOnMonday) {
            currentDow.value - DayOfWeek.MONDAY.value
        } else {
            currentDow.value % 7
        }
        selectedDate.minusDays(offset.toLong())
    }
    val dayNameFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.US) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 0 until 7) {
                val date = startOfWeek.plusDays(i.toLong())
                val dateStr = DateTimeUtils.formatLocalDate(date)
                val isSelected = date == selectedDate
                val isToday = date == today
                val count = remindersByDate[dateStr]?.size ?: 0

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                        .border(
                            width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                            color = if (isToday && !isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectDate(dateStr) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = date.format(dayNameFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (count > 0) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyHourlyTimelineCard(
    selectedDateStr: String,
    dayReminders: List<ReminderEntity>,
    use24Hour: Boolean,
    onEditReminder: (ReminderEntity) -> Unit,
    onCreateAtDate: () -> Unit
) {
    val activeHours = remember(dayReminders) {
        val baseHours = setOf(8, 10, 12, 14, 16, 18, 20)
        val reminderHours = dayReminders.map { DateTimeUtils.parseLocalTime(it.localTimeStr).hour }.toSet()
        (baseHours + reminderHours).sorted()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Daily Time Blocks ($selectedDateStr)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            activeHours.forEach { hour ->
                val hourStr = String.format(Locale.US, "%02d:00", hour)
                val formattedHour = DateTimeUtils.formatDisplayTime(hourStr, use24Hour)
                val matching = dayReminders.filter {
                    DateTimeUtils.parseLocalTime(it.localTimeStr).hour == hour
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formattedHour,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(68.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (matching.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .clickable(onClick = onCreateAtDate)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Open slot",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            matching.forEach { item ->
                                val itemColor = parseHexColor(item.colorHex)
                                Surface(
                                    color = itemColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEditReminder(item) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = DateTimeUtils.formatDisplayTime(item.localTimeStr, use24Hour),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = itemColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCalendarDayCard(
    dateLabel: String,
    onAddClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "No reminders scheduled for $dateLabel",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Tap 'Add Here' to plan a one-time task or recurring activity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Schedule Task")
            }
        }
    }
}

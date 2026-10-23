package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.DateTimeUtils
import com.example.data.ReminderCategory
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import com.example.ui.TaskSortOrder
import com.example.ui.TaskStatusFilter
import com.example.ui.components.ReminderCard
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTasksScreen(
    reminders: List<ReminderEntity>,
    searchQuery: String,
    selectedCategory: ReminderCategory?,
    selectedPriority: ReminderPriority?,
    statusFilter: TaskStatusFilter,
    sortOrder: TaskSortOrder,
    use24Hour: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ReminderCategory?) -> Unit,
    onSelectPriority: (ReminderPriority?) -> Unit,
    onSelectStatusFilter: (TaskStatusFilter) -> Unit,
    onSelectSortOrder: (TaskSortOrder) -> Unit,
    onClearFilters: () -> Unit,
    onOpenCreate: () -> Unit,
    onEditReminder: (ReminderEntity) -> Unit,
    onToggleComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity, Int) -> Unit,
    onTogglePauseRecurrence: (ReminderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = System.currentTimeMillis()
    val todayStr = DateTimeUtils.formatLocalDate(LocalDate.now())
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredAndSorted = remember(
        reminders,
        searchQuery,
        selectedCategory,
        selectedPriority,
        statusFilter,
        sortOrder,
        nowMillis,
        todayStr
    ) {
        val query = searchQuery.trim().lowercase()
        reminders.filter { r ->
            val matchesQuery = query.isEmpty() ||
                r.title.lowercase().contains(query) ||
                r.description.lowercase().contains(query) ||
                r.category.label.lowercase().contains(query)

            val matchesCategory = selectedCategory == null || r.category == selectedCategory
            val matchesPriority = selectedPriority == null || r.priority == selectedPriority

            val matchesStatus = when (statusFilter) {
                TaskStatusFilter.ALL -> true
                TaskStatusFilter.ACTIVE -> !r.isCompleted
                TaskStatusFilter.OVERDUE -> !r.isCompleted && r.effectiveTriggerMillis() < nowMillis
                TaskStatusFilter.TODAY -> r.localDateStr == todayStr
                TaskStatusFilter.RECURRING -> r.isRecurring
                TaskStatusFilter.PAUSED -> r.isRecurring && r.isRecurrencePaused
                TaskStatusFilter.COMPLETED -> r.isCompleted
            }

            matchesQuery && matchesCategory && matchesPriority && matchesStatus
        }.let { list ->
            when (sortOrder) {
                TaskSortOrder.SCHEDULED_TIME ->
                    list.sortedWith(compareBy<ReminderEntity> { it.isCompleted }.thenBy { it.effectiveTriggerMillis() })
                TaskSortOrder.PRIORITY ->
                    list.sortedWith(
                        compareBy<ReminderEntity> { it.isCompleted }
                            .thenByDescending { it.priority.sortOrder }
                            .thenBy { it.effectiveTriggerMillis() }
                    )
                TaskSortOrder.CREATED_DATE ->
                    list.sortedByDescending { it.createdAtEpochMillis }
            }
        }
    }

    val hasActiveFilters = searchQuery.isNotBlank() ||
        selectedCategory != null ||
        selectedPriority != null ||
        statusFilter != TaskStatusFilter.ALL

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("all_tasks_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Sort Menu
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "All Reminders",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${filteredAndSorted.size} of ${reminders.size} task(s) shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("tasks_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort order",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sort: ${sortOrder.label}")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        TaskSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text("Sort by ${order.label}") },
                                onClick = {
                                    showSortMenu = false
                                    onSelectSortOrder(order)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search by title, notes, or category...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tasks_search_input")
            )
        }

        // Status Filter Row
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskStatusFilter.entries) { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { onSelectStatusFilter(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("status_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        // Priority & Category Filter Chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderPriority.entries.forEach { prio ->
                    val isSelected = selectedPriority == prio
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPriority(if (isSelected) null else prio) },
                        label = { Text("${prio.label} Priority") }
                    )
                }

                ReminderCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(if (isSelected) null else cat) },
                        label = { Text(cat.label) }
                    )
                }

                if (hasActiveFilters) {
                    TextButton(
                        onClick = onClearFilters,
                        modifier = Modifier.testTag("clear_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAltOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Filters")
                    }
                }
            }
        }

        // Tasks List or Empty State
        if (filteredAndSorted.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (hasActiveFilters) {
                                "No reminders match your active filters"
                            } else {
                                "Your reminder list is empty"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (hasActiveFilters) {
                                "Try clearing your search or category/status filters to see all tasks."
                            } else {
                                "Create your first reminder or recurring task to receive scheduled native Android alerts."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (hasActiveFilters) {
                                OutlinedButton(onClick = onClearFilters) {
                                    Text("Clear Filters")
                                }
                            }
                            Button(onClick = onOpenCreate) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Reminder")
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredAndSorted, key = { "all_task_${it.id}" }) { reminder ->
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

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

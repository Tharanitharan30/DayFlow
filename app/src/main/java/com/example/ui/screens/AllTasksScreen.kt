package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showFilterSheet by remember { mutableStateOf(false) }

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

    val activeFilterCount = (if (selectedCategory != null) 1 else 0) +
        (if (selectedPriority != null) 1 else 0) +
        (if (statusFilter != TaskStatusFilter.ALL) 1 else 0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("all_tasks_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Slim Header + Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredAndSorted.size} of ${reminders.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Unified Search Bar with integrated Filter Sheet trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search tasks or categories...") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tasks_search_input")
                    )

                    // Filter Button with Badge
                    Box {
                        OutlinedButton(
                            onClick = { showFilterSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("tasks_filter_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filters",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeFilterCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active filter indicator chip row (if any filter is applied)
        if (activeFilterCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Filtered by: ${if (statusFilter != TaskStatusFilter.ALL) statusFilter.label else ""} " +
                            (selectedPriority?.let { "• ${it.label} Priority " } ?: "") +
                            (selectedCategory?.let { "• ${it.label}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = onClearFilters,
                        modifier = Modifier.testTag("clear_filters_button")
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Task Items or Lightweight Empty State
        if (filteredAndSorted.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (activeFilterCount > 0 || searchQuery.isNotBlank()) "No matching tasks found" else "No tasks scheduled",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (activeFilterCount > 0 || searchQuery.isNotBlank()) "Try resetting your search query or filters." else "Tap New Task below to create your first reminder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (activeFilterCount > 0 || searchQuery.isNotBlank()) {
                            OutlinedButton(onClick = onClearFilters) {
                                Text("Clear Filters")
                            }
                        } else {
                            Button(onClick = onOpenCreate) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Task")
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
                    onClick = { onEditReminder(reminder) },
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

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    // Filter & Sort Modal Bottom Sheet
    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FilterAndSortSheetContent(
                statusFilter = statusFilter,
                selectedPriority = selectedPriority,
                selectedCategory = selectedCategory,
                sortOrder = sortOrder,
                onSelectStatus = onSelectStatusFilter,
                onSelectPriority = onSelectPriority,
                onSelectCategory = onSelectCategory,
                onSelectSort = onSelectSortOrder,
                onReset = onClearFilters,
                onClose = { showFilterSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterAndSortSheetContent(
    statusFilter: TaskStatusFilter,
    selectedPriority: ReminderPriority?,
    selectedCategory: ReminderCategory?,
    sortOrder: TaskSortOrder,
    onSelectStatus: (TaskStatusFilter) -> Unit,
    onSelectPriority: (ReminderPriority?) -> Unit,
    onSelectCategory: (ReminderCategory?) -> Unit,
    onSelectSort: (TaskSortOrder) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter & Sort Tasks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onReset) {
                Text("Reset All")
            }
        }

        HorizontalDivider()

        // 1. Status Filter
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { onSelectStatus(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("status_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        // 2. Sort Order
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Sort Order",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskSortOrder.entries.forEach { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { onSelectSort(order) },
                        label = { Text(order.label) }
                    )
                }
            }
        }

        // 3. Priority
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderPriority.entries.forEach { prio ->
                    val isSelected = selectedPriority == prio
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPriority(if (isSelected) null else prio) },
                        label = { Text(prio.label) }
                    )
                }
            }
        }

        // 4. Category
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(if (isSelected) null else cat) },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Apply Filters")
        }
    }
}

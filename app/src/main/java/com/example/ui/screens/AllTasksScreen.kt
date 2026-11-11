package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.example.data.CategoryTagEntity
import com.example.data.DateTimeUtils
import com.example.data.DefaultCategoryTags
import com.example.data.ReminderCategory
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import com.example.ui.TaskSortOrder
import com.example.ui.TaskStatusFilter
import com.example.ui.components.CategoryFilterChipRow
import com.example.ui.components.ReminderCard
import com.example.ui.components.parseHexColor
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    reminders: List<ReminderEntity>,
    categoryTags: List<CategoryTagEntity> = DefaultCategoryTags.presetEntities(),
    searchQuery: String,
    selectedCategory: ReminderCategory?,
    selectedCategoryLabels: Set<String> = emptySet(),
    selectedPriority: ReminderPriority?,
    statusFilter: TaskStatusFilter,
    sortOrder: TaskSortOrder,
    use24Hour: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ReminderCategory?) -> Unit,
    onToggleCategoryLabel: (String) -> Unit = {},
    onClearCategories: () -> Unit = {},
    onCreateCustomTag: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomTag: (String) -> Unit = {},
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

    val effectiveFilterLabels = remember(selectedCategoryLabels, selectedCategory) {
        if (selectedCategoryLabels.isNotEmpty()) {
            selectedCategoryLabels
        } else if (selectedCategory != null) {
            setOf(selectedCategory.shortLabel)
        } else {
            emptySet()
        }
    }

    val filteredAndSorted = remember(
        reminders,
        searchQuery,
        effectiveFilterLabels,
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
                r.effectiveCategoryLabel().lowercase().contains(query) ||
                r.category.label.lowercase().contains(query)

            val matchesCategory = r.matchesCategoryFilter(effectiveFilterLabels)
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

    val activeFilterCount = effectiveFilterLabels.size +
        (if (selectedPriority != null) 1 else 0) +
        (if (statusFilter != TaskStatusFilter.ALL) 1 else 0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("all_tasks_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Minimalist Material 3 Header + Outlined Search Bar + Multi-Select Category Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tasks & Labels",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tag and filter tasks by Room categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "${filteredAndSorted.size} of ${reminders.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Minimalist Outlined Search Field + Filter Sheet Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search tasks, notes, or labels...") },
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
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tasks_search_input")
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showFilterSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
                                    .size(19.dp)
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

                // Multi-Select Category Filter Chip Bar with Task Counts
                CategoryFilterChipRow(
                    reminders = reminders,
                    categoryTags = categoryTags,
                    selectedCategoryLabels = effectiveFilterLabels,
                    onToggleCategoryLabel = onToggleCategoryLabel,
                    onClearCategories = onClearCategories,
                    onCreateCustomTag = onCreateCustomTag,
                    onDeleteCustomTag = onDeleteCustomTag
                )
            }
        }

        // Active filter summary strip (if any filter is applied)
        if (activeFilterCount > 0) {
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val summaryParts = buildList {
                            if (effectiveFilterLabels.isNotEmpty()) {
                                add("Labels: ${effectiveFilterLabels.joinToString(", ")}")
                            }
                            if (statusFilter != TaskStatusFilter.ALL) {
                                add("Status: ${statusFilter.label}")
                            }
                            if (selectedPriority != null) {
                                add("${selectedPriority.label} Priority")
                            }
                        }
                        Text(
                            text = summaryParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onClearFilters,
                            modifier = Modifier.testTag("clear_filters_button")
                        ) {
                            Text("Reset All", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Task Items or Minimalist Outlined Empty State
        if (filteredAndSorted.isEmpty()) {
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            text = if (activeFilterCount > 0 || searchQuery.isNotBlank()) "No tasks match selected filters" else "No tasks scheduled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (activeFilterCount > 0 || searchQuery.isNotBlank()) {
                                "Try toggling different category chips or resetting your active filters."
                            } else {
                                "Tap New Task below to create and tag your first task."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (activeFilterCount > 0 || searchQuery.isNotBlank()) {
                            OutlinedButton(
                                onClick = onClearFilters,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Clear Filters")
                            }
                        } else {
                            Button(
                                onClick = onOpenCreate,
                                shape = RoundedCornerShape(10.dp)
                            ) {
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
                categoryTags = categoryTags,
                selectedCategoryLabels = effectiveFilterLabels,
                sortOrder = sortOrder,
                onSelectStatus = onSelectStatusFilter,
                onSelectPriority = onSelectPriority,
                onToggleCategoryLabel = onToggleCategoryLabel,
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
    categoryTags: List<CategoryTagEntity>,
    selectedCategoryLabels: Set<String>,
    sortOrder: TaskSortOrder,
    onSelectStatus: (TaskStatusFilter) -> Unit,
    onSelectPriority: (ReminderPriority?) -> Unit,
    onToggleCategoryLabel: (String) -> Unit,
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

        // 1. Category Labels (Multi-Select)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Categories (Multi-Select)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categoryTags.forEach { tag ->
                    val isSelected = selectedCategoryLabels.any { it.equals(tag.name, ignoreCase = true) }
                    val tagColor = parseHexColor(tag.colorHex, MaterialTheme.colorScheme.primary)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleCategoryLabel(tag.name) },
                        label = { Text(tag.name) },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = tagColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(tagColor)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tagColor.copy(alpha = 0.14f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = tagColor.copy(alpha = 0.65f)
                        )
                    )
                }
            }
        }

        // 2. Status Filter
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

        // 3. Sort Order
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

        // 4. Priority
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

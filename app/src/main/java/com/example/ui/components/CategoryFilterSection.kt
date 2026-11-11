package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CategoryTagEntity
import com.example.data.DefaultCategoryTags
import com.example.data.ReminderEntity

/**
 * Minimalist Google Material 3 Multi-Select Category Filter Bar with live task counts
 * and custom Room category label creation.
 */
@Composable
fun CategoryFilterChipRow(
    reminders: List<ReminderEntity>,
    categoryTags: List<CategoryTagEntity>,
    selectedCategoryLabels: Set<String>,
    onToggleCategoryLabel: (String) -> Unit,
    onClearCategories: () -> Unit,
    onCreateCustomTag: (String, String) -> Unit,
    onDeleteCustomTag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val countsByLabel = remember(reminders, categoryTags) {
        val map = mutableMapOf<String, Int>()
        categoryTags.forEach { tag ->
            val count = reminders.count { r ->
                !r.isCompleted && r.effectiveCategoryLabel().equals(tag.name, ignoreCase = true)
            }
            map[tag.name] = count
        }
        map
    }

    val totalActiveCount = remember(reminders) {
        reminders.count { !it.isCompleted }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_filter_chip_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        // "All" Chip
        item(key = "cat_chip_all") {
            val allSelected = selectedCategoryLabels.isEmpty()
            FilterChip(
                selected = allSelected,
                onClick = onClearCategories,
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                        CategoryCountPill(
                            count = totalActiveCount,
                            selected = allSelected,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                leadingIcon = if (allSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                shape = RoundedCornerShape(10.dp),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = allSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.testTag("filter_chip_all")
            )
        }

        // Preset & Custom Room Category Chips
        items(categoryTags, key = { "cat_chip_${it.name.lowercase()}" }) { tag ->
            val isSelected = selectedCategoryLabels.any { it.equals(tag.name, ignoreCase = true) }
            val tagColor = parseHexColor(tag.colorHex, MaterialTheme.colorScheme.primary)
            val count = countsByLabel[tag.name] ?: 0

            FilterChip(
                selected = isSelected,
                onClick = { onToggleCategoryLabel(tag.name) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                        CategoryCountPill(
                            count = count,
                            selected = isSelected,
                            accentColor = tagColor
                        )
                    }
                },
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
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tagColor.copy(alpha = 0.14f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = tagColor.copy(alpha = 0.65f)
                ),
                modifier = Modifier.testTag("filter_chip_${tag.name.lowercase()}")
            )
        }

        // "+ Label" AssistChip to create custom Room category tag
        item(key = "cat_chip_add_custom") {
            AssistChip(
                onClick = { showCreateDialog = true },
                label = {
                    Text(
                        text = "New Label",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Custom Category",
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.testTag("add_custom_category_chip")
            )
        }
    }

    if (showCreateDialog) {
        CreateCategoryTagDialog(
            existingTags = categoryTags,
            onDismiss = { showCreateDialog = false },
            onCreateTag = { name, hex ->
                onCreateCustomTag(name, hex)
                showCreateDialog = false
            },
            onDeleteCustomTag = onDeleteCustomTag
        )
    }
}

@Composable
private fun CategoryCountPill(
    count: Int,
    selected: Boolean,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) {
            accentColor.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        }
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateCategoryTagDialog(
    existingTags: List<CategoryTagEntity>,
    onDismiss: () -> Unit,
    onCreateTag: (name: String, colorHex: String) -> Unit,
    onDeleteCustomTag: ((String) -> Unit)? = null
) {
    var nameInput by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(DefaultCategoryTags.SwatchPalette.first()) }
    val customTags = remember(existingTags) { existingTags.filter { !it.isPreset } }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Custom Category Label",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Create a custom label saved in the Room database to tag and filter your tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 24) nameInput = it },
                    label = { Text("Label Name") },
                    placeholder = { Text("e.g., Deep Work, Study, Fitness") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_name_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Label Accent Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DefaultCategoryTags.SwatchPalette.forEach { hex ->
                            val swatchColor = parseHexColor(hex)
                            val isSelected = selectedHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedHex = hex }
                                    .testTag("swatch_${hex.removePrefix("#")}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected color",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (customTags.isNotEmpty() && onDeleteCustomTag != null) {
                    OutlinedCard(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Saved Custom Labels",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                customTags.forEach { custom ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(parseHexColor(custom.colorHex))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = custom.name,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            IconButton(
                                                onClick = { onDeleteCustomTag(custom.name) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Delete ${custom.name}",
                                                    modifier = Modifier.size(13.dp)
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onCreateTag(trimmed, selectedHex)
                    }
                },
                enabled = nameInput.trim().isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_custom_category_button")
            ) {
                Text("Save Label")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

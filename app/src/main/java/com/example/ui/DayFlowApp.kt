package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ReminderEditorSheet
import com.example.ui.screens.AllTasksScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.JsonBackupRestoreDialog
import com.example.ui.screens.NotificationDiagnosticsScreen
import com.example.ui.screens.OnboardingDialog
import com.example.ui.screens.SettingsAndDiagnosticsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavDestination(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(
        tab = MainTab.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    NavDestination(
        tab = MainTab.CALENDAR,
        label = "Calendar",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_tab_calendar"
    ),
    NavDestination(
        tab = MainTab.TASKS,
        label = "All Tasks",
        selectedIcon = Icons.Filled.Checklist,
        unselectedIcon = Icons.Outlined.Checklist,
        testTag = "nav_tab_tasks"
    ),
    NavDestination(
        tab = MainTab.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
fun DayFlowApp(viewModel: DayFlowViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = state.navState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh diagnostics when returning from Android system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back button handling for secondary tabs
    BackHandler(enabled = !navState.showDiagnosticsSheet && navState.currentTab != MainTab.HOME) {
        viewModel.selectTab(MainTab.HOME)
    }

    // Runtime permission launcher for Android 13+ POST_NOTIFICATIONS
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshDiagnostics()
        viewModel.showMessage(
            if (granted) {
                "Notification permission granted!"
            } else {
                "Notification permission denied. You can enable it anytime in Notification Diagnostics."
            }
        )
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshDiagnostics()
            viewModel.showMessage("Notifications are enabled by default on this Android version.")
        }
    }

    // SAF File Export Launcher for JSON Backup (Reminders + Tasks + Settings)
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.repository.exportRemindersToJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    viewModel.showMessage("Exported reminders & settings backup to JSON file")
                }.onFailure {
                    viewModel.showMessage("Failed to write JSON file: ${it.localizedMessage}")
                }
            }
        }
    }

    // SAF File Import Launcher for JSON Restore
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            it.readText()
                        } ?: ""
                    }
                    if (content.isNotBlank()) {
                        viewModel.importFromJsonString(
                            jsonString = content,
                            replaceExisting = false,
                            restoreSettings = true
                        )
                    }
                }.onFailure {
                    viewModel.showMessage("Failed to import JSON file: ${it.localizedMessage}")
                }
            }
        }
    }

    LaunchedEffect(navState.snackbarMessage) {
        val msg = navState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (!navState.showDiagnosticsSheet && navState.currentTab != MainTab.SETTINGS) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            val presetDate = if (navState.currentTab == MainTab.CALENDAR) {
                                navState.selectedDateStr
                            } else {
                                null
                            }
                            viewModel.openCreateReminder(presetDate)
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Add Reminder") },
                        text = { Text("New Task") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_quick_add")
                    )
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        Destinations.forEach { dest ->
                            val selected = !navState.showDiagnosticsSheet && navState.currentTab == dest.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(modifier = Modifier.testTag("navigation_rail")) {
                        Destinations.forEach { dest ->
                            val selected = !navState.showDiagnosticsSheet && navState.currentTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                if (navState.showDiagnosticsSheet) {
                    NotificationDiagnosticsScreen(
                        diagnostics = state.diagnostics,
                        lastReconciledMillis = state.settings.lastReconciledEpochMillis,
                        use24Hour = state.settings.use24HourFormat,
                        alarmHistory = state.alarmHistory,
                        onBack = { viewModel.setDiagnosticsSheetOpen(false) },
                        onRequestNotificationPermission = requestNotificationPermission,
                        onSendTestNotification = { viewModel.sendTestNotification() },
                        onTriggerTestRingingAlarm = { viewModel.triggerTestRingingAlarm() },
                        onClearAlarmHistory = { viewModel.clearAlarmHistory() },
                        onReconcileAlarms = { viewModel.reconcileAlarmsNow() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    when (navState.currentTab) {
                        MainTab.HOME -> {
                            DashboardScreen(
                                reminders = state.reminders,
                                categoryTags = state.categoryTags,
                                selectedCategoryLabels = navState.selectedCategoryLabels,
                                use24Hour = state.settings.use24HourFormat,
                                diagnostics = state.diagnostics,
                                onQuickAdd = { title -> viewModel.quickAddReminder(title) },
                                onOpenFullCreate = { viewModel.openCreateReminder() },
                                onEditReminder = { viewModel.openEditReminder(it) },
                                onToggleComplete = { viewModel.toggleCompleted(it) },
                                onSnooze = { r, mins -> viewModel.snoozeReminder(r, mins) },
                                onTogglePauseRecurrence = { viewModel.togglePauseRecurrence(it) },
                                onToggleCategoryLabel = { viewModel.toggleCategoryLabelFilter(it) },
                                onClearCategories = { viewModel.clearCategoryFilters() },
                                onCreateCustomTag = { name, hex -> viewModel.createCustomCategoryTag(name, hex) },
                                onDeleteCustomTag = { name -> viewModel.deleteCustomCategoryTag(name) },
                                onOpenDiagnostics = { viewModel.setDiagnosticsSheetOpen(true) },
                                onClearCompleted = { viewModel.clearCompletedReminders() },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        MainTab.CALENDAR -> {
                            CalendarScreen(
                                reminders = state.reminders,
                                selectedDateStr = navState.selectedDateStr,
                                viewMode = navState.calendarViewMode,
                                use24Hour = state.settings.use24HourFormat,
                                weekStartsOnMonday = state.settings.weekStartsOnMonday,
                                onSelectDate = { viewModel.selectCalendarDate(it) },
                                onChangeViewMode = { viewModel.setCalendarViewMode(it) },
                                onOpenCreateForDate = { dateStr -> viewModel.openCreateReminder(dateStr) },
                                onEditReminder = { viewModel.openEditReminder(it) },
                                onToggleComplete = { viewModel.toggleCompleted(it) },
                                onSnooze = { r, mins -> viewModel.snoozeReminder(r, mins) },
                                onTogglePauseRecurrence = { viewModel.togglePauseRecurrence(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        MainTab.TASKS -> {
                            AllTasksScreen(
                                reminders = state.reminders,
                                categoryTags = state.categoryTags,
                                searchQuery = navState.searchQuery,
                                selectedCategory = navState.selectedCategory,
                                selectedCategoryLabels = navState.selectedCategoryLabels,
                                selectedPriority = navState.selectedPriority,
                                statusFilter = navState.statusFilter,
                                sortOrder = navState.sortOrder,
                                use24Hour = state.settings.use24HourFormat,
                                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                onSelectCategory = { viewModel.selectCategoryFilter(it) },
                                onToggleCategoryLabel = { viewModel.toggleCategoryLabelFilter(it) },
                                onClearCategories = { viewModel.clearCategoryFilters() },
                                onCreateCustomTag = { name, hex -> viewModel.createCustomCategoryTag(name, hex) },
                                onDeleteCustomTag = { name -> viewModel.deleteCustomCategoryTag(name) },
                                onSelectPriority = { viewModel.selectPriorityFilter(it) },
                                onSelectStatusFilter = { viewModel.selectStatusFilter(it) },
                                onSelectSortOrder = { viewModel.selectSortOrder(it) },
                                onClearFilters = { viewModel.clearFilters() },
                                onOpenCreate = { viewModel.openCreateReminder() },
                                onEditReminder = { viewModel.openEditReminder(it) },
                                onToggleComplete = { viewModel.toggleCompleted(it) },
                                onSnooze = { r, mins -> viewModel.snoozeReminder(r, mins) },
                                onTogglePauseRecurrence = { viewModel.togglePauseRecurrence(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        MainTab.SETTINGS -> {
                            SettingsAndDiagnosticsScreen(
                                settings = state.settings,
                                diagnostics = state.diagnostics,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onSendTestNotification = { viewModel.sendTestNotification() },
                                onTriggerTestRingingAlarm = { viewModel.triggerTestRingingAlarm() },
                                onReconcileAlarms = { viewModel.reconcileAlarmsNow() },
                                onOpenDiagnosticsModal = { viewModel.setDiagnosticsSheetOpen(true) },
                                onOpenJsonBackupDialog = { viewModel.openJsonBackupDialog() },
                                onExportJsonToFile = {
                                    exportJsonLauncher.launch("dayflow_backup.json")
                                },
                                onImportJsonFromFile = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onSetUse24Hour = { viewModel.setUse24HourFormat(it) },
                                onSetWeekStartsMonday = { viewModel.setWeekStartsOnMonday(it) },
                                onSetDefaultPriority = { viewModel.setDefaultPriority(it) },
                                onSetDefaultCategory = { viewModel.setDefaultCategory(it) },
                                onSetDefaultAdvanceMinutes = { viewModel.setDefaultAdvanceMinutes(it) },
                                onSetSoundEnabled = { viewModel.setSoundEnabled(it) },
                                onSetVibrationEnabled = { viewModel.setVibrationEnabled(it) },
                                onReopenOnboarding = { viewModel.reopenOnboarding() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Create / Edit Reminder Bottom Sheet
    val editingReminder = navState.editingReminder
    if (navState.isEditorOpen && editingReminder != null) {
        ReminderEditorSheet(
            initialReminder = editingReminder,
            use24Hour = state.settings.use24HourFormat,
            categoryTags = state.categoryTags,
            onCreateCustomTag = { name, hex -> viewModel.createCustomCategoryTag(name, hex) },
            onDeleteCustomTag = { name -> viewModel.deleteCustomCategoryTag(name) },
            onDismiss = { viewModel.closeEditor() },
            onSave = { updated, scopeMode -> viewModel.saveReminder(updated, scopeMode) },
            onDelete = { target, entireSeries -> viewModel.deleteReminder(target, entireSeries) }
        )
    }

    // JSON Export / Import Inspector Dialog
    if (navState.showJsonBackupDialog) {
        JsonBackupRestoreDialog(
            initialJson = navState.exportedJsonPreview,
            onDismiss = { viewModel.closeJsonBackupDialog() },
            onImportJson = { json, replace, restoreSettings ->
                viewModel.importFromJsonString(json, replace, restoreSettings)
            },
            onCopiedMessage = { viewModel.showMessage("Copied JSON backup to clipboard") }
        )
    }

    // First-Launch Onboarding Flow
    if (!state.settings.onboardingCompleted) {
        OnboardingDialog(
            settings = state.settings,
            diagnostics = state.diagnostics,
            onRequestNotificationPermission = requestNotificationPermission,
            onSetThemeMode = { viewModel.setThemeMode(it) },
            onSetUse24Hour = { viewModel.setUse24HourFormat(it) },
            onCompleteOnboarding = { viewModel.completeOnboarding() }
        )
    }
}

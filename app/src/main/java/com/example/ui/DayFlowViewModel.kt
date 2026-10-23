package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmHistoryEntity
import com.example.data.DateTimeUtils
import com.example.data.DayFlowDatabase
import com.example.data.RecurrenceType
import com.example.data.RecurringEditScope
import com.example.data.ReminderCategory
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import com.example.data.ReminderRepository
import com.example.data.TaskRepository
import com.example.data.ThemeMode
import com.example.data.UserPreferencesRepository
import com.example.data.UserSettings
import com.example.notifications.AlarmService
import com.example.notifications.NotificationDiagnostics
import com.example.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab(val route: String) {
    HOME("home"),
    CALENDAR("calendar"),
    TASKS("all_tasks"),
    SETTINGS("settings")
}

enum class CalendarViewMode(val label: String) {
    MONTHLY("Month"),
    WEEKLY("Week"),
    DAILY("Day"),
    AGENDA("Agenda")
}

enum class TaskStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    OVERDUE("Overdue"),
    TODAY("Today"),
    RECURRING("Recurring"),
    PAUSED("Paused"),
    COMPLETED("Completed")
}

enum class TaskSortOrder(val label: String) {
    SCHEDULED_TIME("Time"),
    PRIORITY("Priority"),
    CREATED_DATE("Created")
}

data class FilterAndNavigationState(
    val currentTab: MainTab = MainTab.HOME,
    val calendarViewMode: CalendarViewMode = CalendarViewMode.MONTHLY,
    val selectedDateStr: String = DateTimeUtils.formatLocalDate(LocalDate.now()),
    val searchQuery: String = "",
    val selectedCategory: ReminderCategory? = null,
    val selectedPriority: ReminderPriority? = null,
    val statusFilter: TaskStatusFilter = TaskStatusFilter.ALL,
    val sortOrder: TaskSortOrder = TaskSortOrder.SCHEDULED_TIME,
    val isEditorOpen: Boolean = false,
    val editingReminder: ReminderEntity? = null,
    val showDiagnosticsSheet: Boolean = false,
    val showJsonBackupDialog: Boolean = false,
    val exportedJsonPreview: String = "",
    val snackbarMessage: String? = null
)

data class DayFlowUiState(
    val reminders: List<ReminderEntity> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val navState: FilterAndNavigationState = FilterAndNavigationState(),
    val diagnostics: NotificationDiagnostics? = null,
    val alarmHistory: List<AlarmHistoryEntity> = emptyList()
)

class DayFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DayFlowDatabase.getInstance(application)
    private val preferencesRepository = UserPreferencesRepository(application)
    val scheduler = ReminderScheduler(application)
    val repository = ReminderRepository(
        dao = database.reminderDao(),
        scheduler = scheduler,
        preferencesRepository = preferencesRepository,
        taskDao = database.taskDao()
    )
    val taskRepository = TaskRepository(database.taskDao(), scheduler)

    private val navStateFlow = MutableStateFlow(FilterAndNavigationState())
    private val diagnosticsRefreshTrigger = MutableStateFlow(0L)

    val uiState: StateFlow<DayFlowUiState> = combine(
        repository.allRemindersFlow,
        preferencesRepository.settingsFlow,
        navStateFlow,
        database.alarmHistoryDao().getRecentHistoryFlow(),
        diagnosticsRefreshTrigger
    ) { reminders, settings, navState, history, _ ->
        val diag = scheduler.getDiagnostics(reminders)
        DayFlowUiState(
            reminders = reminders,
            settings = settings,
            navState = navState,
            diagnostics = diag,
            alarmHistory = history
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DayFlowUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialSampleDataIfNeeded()
            repository.reconcileAllRemindersWithSystemClock(ZoneId.systemDefault())
            refreshDiagnostics()
        }
    }

    fun refreshDiagnostics() {
        diagnosticsRefreshTrigger.value = System.currentTimeMillis()
    }

    fun selectTab(tab: MainTab) {
        navStateFlow.update { it.copy(currentTab = tab, showDiagnosticsSheet = false) }
    }

    fun setCalendarViewMode(mode: CalendarViewMode) {
        navStateFlow.update { it.copy(calendarViewMode = mode) }
    }

    fun selectCalendarDate(dateStr: String) {
        navStateFlow.update { it.copy(selectedDateStr = dateStr) }
    }

    fun updateSearchQuery(query: String) {
        navStateFlow.update { it.copy(searchQuery = query) }
    }

    fun selectCategoryFilter(category: ReminderCategory?) {
        navStateFlow.update { it.copy(selectedCategory = category) }
    }

    fun selectPriorityFilter(priority: ReminderPriority?) {
        navStateFlow.update { it.copy(selectedPriority = priority) }
    }

    fun selectStatusFilter(filter: TaskStatusFilter) {
        navStateFlow.update { it.copy(statusFilter = filter) }
    }

    fun selectSortOrder(order: TaskSortOrder) {
        navStateFlow.update { it.copy(sortOrder = order) }
    }

    fun clearFilters() {
        navStateFlow.update {
            it.copy(
                searchQuery = "",
                selectedCategory = null,
                selectedPriority = null,
                statusFilter = TaskStatusFilter.ALL
            )
        }
    }

    fun openCreateReminder(presetDateStr: String? = null) {
        val settings = uiState.value.settings
        val dateStr = presetDateStr ?: DateTimeUtils.formatLocalDate(LocalDate.now())
        val nextHour = LocalTime.now().plusHours(1).withMinute(0)
        val timeStr = DateTimeUtils.formatLocalTime(nextHour)
        val defaultOffsets = if (settings.defaultAdvanceMinutes > 0) {
            "0,${settings.defaultAdvanceMinutes}"
        } else {
            "0"
        }

        val draft = ReminderEntity(
            id = 0L,
            title = "",
            description = "",
            localDateStr = dateStr,
            localTimeStr = timeStr,
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(dateStr, timeStr),
            category = settings.defaultCategory,
            colorHex = settings.defaultCategory.defaultColorHex,
            priority = settings.defaultPriority,
            recurrenceType = RecurrenceType.NONE,
            advanceAlertOffsetsMinutes = defaultOffsets
        )
        navStateFlow.update {
            it.copy(isEditorOpen = true, editingReminder = draft)
        }
    }

    fun openEditReminder(reminder: ReminderEntity) {
        navStateFlow.update {
            it.copy(isEditorOpen = true, editingReminder = reminder)
        }
    }

    fun openReminderById(reminderId: Long) {
        if (reminderId <= 0L) return
        viewModelScope.launch {
            val found = repository.getReminderByIdSync(reminderId)
            if (found != null) {
                navStateFlow.update {
                    it.copy(isEditorOpen = true, editingReminder = found)
                }
            }
        }
    }

    fun closeEditor() {
        navStateFlow.update {
            it.copy(isEditorOpen = false, editingReminder = null)
        }
    }

    fun saveReminder(
        reminder: ReminderEntity,
        editScope: RecurringEditScope = RecurringEditScope.THIS_OCCURRENCE_ONLY
    ) {
        if (reminder.title.isBlank()) {
            showMessage("Please enter a title for the reminder.")
            return
        }
        viewModelScope.launch {
            val isNew = reminder.id == 0L
            repository.createOrUpdateReminder(reminder.copy(title = reminder.title.trim()), editScope)
            navStateFlow.update {
                it.copy(
                    isEditorOpen = false,
                    editingReminder = null,
                    snackbarMessage = if (isNew) "Reminder scheduled" else "Reminder updated"
                )
            }
            refreshDiagnostics()
        }
    }

    fun quickAddReminder(
        title: String,
        dateStr: String = DateTimeUtils.formatLocalDate(LocalDate.now()),
        timeStr: String = DateTimeUtils.formatLocalTime(LocalTime.now().plusHours(1).withMinute(0)),
        priority: ReminderPriority = uiState.value.settings.defaultPriority,
        category: ReminderCategory = uiState.value.settings.defaultCategory
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val defaultAdvance = uiState.value.settings.defaultAdvanceMinutes
            val offsets = if (defaultAdvance > 0) "0,$defaultAdvance" else "0"
            val entity = ReminderEntity(
                title = trimmed,
                localDateStr = dateStr,
                localTimeStr = timeStr,
                scheduledEpochMillis = DateTimeUtils.computeEpochMillis(dateStr, timeStr),
                category = category,
                colorHex = category.defaultColorHex,
                priority = priority,
                advanceAlertOffsetsMinutes = offsets
            )
            repository.createOrUpdateReminder(entity)
            showMessage("Added \"$trimmed\"")
            refreshDiagnostics()
        }
    }

    fun toggleCompleted(reminder: ReminderEntity) {
        viewModelScope.launch {
            val newCompleted = !reminder.isCompleted
            repository.setReminderCompleted(reminder.id, newCompleted)
            val msg = when {
                newCompleted && reminder.isRecurring && !reminder.isRecurrencePaused ->
                    "Completed! Next recurring occurrence scheduled."
                newCompleted -> "Marked as completed"
                else -> "Restored to active reminders"
            }
            showMessage(msg)
            refreshDiagnostics()
        }
    }

    fun snoozeReminder(reminder: ReminderEntity, minutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminder(reminder.id, minutes)
            showMessage("Snoozed \"${reminder.title}\" for $minutes minutes")
            refreshDiagnostics()
        }
    }

    fun togglePauseRecurrence(reminder: ReminderEntity) {
        viewModelScope.launch {
            val newPaused = !reminder.isRecurrencePaused
            repository.setRecurrencePaused(reminder.id, newPaused)
            showMessage(
                if (newPaused) "Recurring series paused" else "Recurring series resumed & rescheduled"
            )
            refreshDiagnostics()
        }
    }

    fun deleteReminder(reminder: ReminderEntity, deleteEntireSeries: Boolean = false) {
        viewModelScope.launch {
            repository.deleteReminder(reminder.id, deleteEntireSeries)
            navStateFlow.update {
                it.copy(
                    isEditorOpen = false,
                    editingReminder = null,
                    snackbarMessage = if (deleteEntireSeries) "Recurring series deleted" else "Reminder deleted"
                )
            }
            refreshDiagnostics()
        }
    }

    fun clearCompletedReminders() {
        viewModelScope.launch {
            repository.deleteCompletedReminders()
            showMessage("Cleared completed reminders")
            refreshDiagnostics()
        }
    }

    fun reconcileAlarmsNow() {
        viewModelScope.launch {
            val count = repository.reconcileAllRemindersWithSystemClock(ZoneId.systemDefault())
            refreshDiagnostics()
            showMessage("Reconciled & scheduled $count active reminder(s)")
        }
    }

    fun sendTestNotification() {
        val sent = scheduler.sendImmediateTestNotification()
        if (sent) {
            showMessage("Test notification sent! Check your status bar.")
        } else {
            showMessage("Notification permission required. Enable it in Diagnostics.")
        }
        refreshDiagnostics()
    }

    fun triggerTestRingingAlarm() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val historyEntry = AlarmHistoryEntity(
                reminderId = 999999L,
                taskTitle = "Test Ringing Alarm",
                scheduledEpochMillis = now,
                actualTriggerEpochMillis = now + 400L,
                delaySeconds = 0L,
                isRinging = true,
                triggerSource = "TEST_ALARM"
            )
            try {
                database.alarmHistoryDao().insert(historyEntry)
                database.alarmHistoryDao().pruneToLastTen()
            } catch (_: Exception) {}

            val intent = Intent(app, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START_ALARM
                putExtra(AlarmService.EXTRA_REMINDER_ID, 999999L)
                putExtra(AlarmService.EXTRA_TITLE, "Test Ringing Alarm")
                putExtra(AlarmService.EXTRA_DESCRIPTION, "Real audible Clock-style task alarm preview")
                putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, 5)
            }
            try {
                ContextCompat.startForegroundService(app, intent)
            } catch (_: Exception) {
                app.startService(intent)
            }
            val activityIntent = Intent(app, AlarmActivity::class.java).apply {
                putExtra(AlarmService.EXTRA_REMINDER_ID, 999999L)
                putExtra(AlarmService.EXTRA_TITLE, "Test Ringing Alarm")
                putExtra(AlarmService.EXTRA_DESCRIPTION, "Real audible Clock-style task alarm preview")
                putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, 5)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                app.startActivity(activityIntent)
            } catch (_: Exception) {}
        }
    }

    fun clearAlarmHistory() {
        viewModelScope.launch {
            database.alarmHistoryDao().clearAllHistory()
            showMessage("Cleared alarm ringing history")
        }
    }

    fun setDiagnosticsSheetOpen(open: Boolean) {
        refreshDiagnostics()
        navStateFlow.update { it.copy(showDiagnosticsSheet = open) }
    }

    fun openJsonBackupDialog() {
        viewModelScope.launch {
            val json = repository.exportRemindersToJson()
            navStateFlow.update {
                it.copy(showJsonBackupDialog = true, exportedJsonPreview = json)
            }
        }
    }

    fun closeJsonBackupDialog() {
        navStateFlow.update { it.copy(showJsonBackupDialog = false) }
    }

    fun importFromJsonString(
        jsonString: String,
        replaceExisting: Boolean,
        restoreSettings: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val summary = repository.importBackupFromJson(
                    jsonString = jsonString,
                    replaceExisting = replaceExisting,
                    restoreSettings = restoreSettings
                )
                val settingsSuffix = if (summary.settingsRestored) " & restored settings" else ""
                navStateFlow.update {
                    it.copy(
                        showJsonBackupDialog = false,
                        snackbarMessage = "Imported ${summary.remindersImported} reminder(s)$settingsSuffix"
                    )
                }
                refreshDiagnostics()
            } catch (e: Exception) {
                showMessage("Invalid JSON backup format: ${e.localizedMessage ?: "Parse error"}")
            }
        }
    }

    // Settings updates
    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            refreshDiagnostics()
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(false)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setUse24HourFormat(use24Hour: Boolean) {
        viewModelScope.launch { preferencesRepository.setUse24HourFormat(use24Hour) }
    }

    fun setWeekStartsOnMonday(monday: Boolean) {
        viewModelScope.launch { preferencesRepository.setWeekStartsOnMonday(monday) }
    }

    fun setDefaultPriority(priority: ReminderPriority) {
        viewModelScope.launch { preferencesRepository.setDefaultPriority(priority) }
    }

    fun setDefaultCategory(category: ReminderCategory) {
        viewModelScope.launch { preferencesRepository.setDefaultCategory(category) }
    }

    fun setDefaultAdvanceMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultAdvanceMinutes(minutes) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVibrationEnabled(enabled) }
    }

    fun clearSnackbarMessage() {
        navStateFlow.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(message: String) {
        navStateFlow.update { it.copy(snackbarMessage = message) }
    }
}

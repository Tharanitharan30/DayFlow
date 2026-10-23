package com.example.data

import com.example.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class BackupImportSummary(
    val remindersImported: Int,
    val tasksImported: Int,
    val settingsRestored: Boolean
)

class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val preferencesRepository: UserPreferencesRepository,
    private val taskDao: TaskDao? = null
) {
    val allRemindersFlow: Flow<List<ReminderEntity>> = dao.observeAllReminders()

    /**
     * Seeds realistic, helpful initial reminders on first launch so the user can explore
     * today's tasks, overdue items, upcoming timeline, and recurring tasks immediately.
     */
    suspend fun ensureInitialSampleDataIfNeeded() {
        val alreadySeeded = preferencesRepository.isInitialSeedDoneFlow.first()
        if (alreadySeeded) return
        val existingCount = dao.getReminderCountSync()
        if (existingCount > 0) {
            preferencesRepository.markInitialSeedDone()
            return
        }

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val nowTime = LocalTime.now()

        val morningTimeStr = DateTimeUtils.formatLocalTime(LocalTime.of(8, 30))
        val upcomingHour = nowTime.plusHours(2).withMinute(0)
        val upcomingTimeStr = DateTimeUtils.formatLocalTime(upcomingHour)
        val eveningTimeStr = DateTimeUtils.formatLocalTime(LocalTime.of(19, 30))

        val samples = listOf(
            ReminderEntity(
                title = "Morning Hydration & 10-Min Stretch",
                description = "Drink 500ml water and complete upper-back mobility routine before checking messages.",
                localDateStr = DateTimeUtils.formatLocalDate(today),
                localTimeStr = morningTimeStr,
                timezoneId = zone.id,
                scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                    DateTimeUtils.formatLocalDate(today),
                    morningTimeStr,
                    zone
                ),
                category = ReminderCategory.HEALTH,
                colorHex = ReminderCategory.HEALTH.defaultColorHex,
                priority = ReminderPriority.MEDIUM,
                recurrenceType = RecurrenceType.DAILY,
                recurrenceInterval = 1,
                advanceAlertOffsetsMinutes = "0",
                isCompleted = true,
                completedAtEpochMillis = System.currentTimeMillis() - 3_600_000L
            ),
            ReminderEntity(
                title = "Review Weekly Sprint & Top 3 Priorities",
                description = "Check calendar blocks, unblock pending deliverables, and confirm afternoon milestones.",
                localDateStr = DateTimeUtils.formatLocalDate(today),
                localTimeStr = upcomingTimeStr,
                timezoneId = zone.id,
                scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                    DateTimeUtils.formatLocalDate(today),
                    upcomingTimeStr,
                    zone
                ),
                category = ReminderCategory.WORK,
                colorHex = ReminderCategory.WORK.defaultColorHex,
                priority = ReminderPriority.HIGH,
                recurrenceType = RecurrenceType.SELECTED_WEEKDAYS,
                recurrenceWeekdays = "1,2,3,4,5",
                advanceAlertOffsetsMinutes = "0,10"
            ),
            ReminderEntity(
                title = "Evening Digital Sunset & Plan Tomorrow",
                description = "Review completed tasks in DayFlow, capture open loops, and set alarm for tomorrow.",
                localDateStr = DateTimeUtils.formatLocalDate(today),
                localTimeStr = eveningTimeStr,
                timezoneId = zone.id,
                scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                    DateTimeUtils.formatLocalDate(today),
                    eveningTimeStr,
                    zone
                ),
                category = ReminderCategory.HABIT,
                colorHex = ReminderCategory.HABIT.defaultColorHex,
                priority = ReminderPriority.LOW,
                recurrenceType = RecurrenceType.DAILY,
                recurrenceInterval = 1,
                advanceAlertOffsetsMinutes = "0,15"
            ),
            ReminderEntity(
                title = "Pay Monthly Cloud & Utility Statements",
                description = "Verify auto-debit confirmation and archive PDF receipts in finance folder.",
                localDateStr = DateTimeUtils.formatLocalDate(today.plusDays(1)),
                localTimeStr = "10:00",
                timezoneId = zone.id,
                scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                    DateTimeUtils.formatLocalDate(today.plusDays(1)),
                    "10:00",
                    zone
                ),
                category = ReminderCategory.FINANCE,
                colorHex = ReminderCategory.FINANCE.defaultColorHex,
                priority = ReminderPriority.HIGH,
                recurrenceType = RecurrenceType.MONTHLY,
                recurrenceInterval = 1,
                advanceAlertOffsetsMinutes = "0,60"
            )
        )

        samples.forEach { sample ->
            val id = dao.insertReminder(sample)
            val saved = sample.copy(id = id)
            scheduler.scheduleReminder(saved)
        }
        preferencesRepository.markInitialSeedDone()
    }

    suspend fun createOrUpdateReminder(
        reminder: ReminderEntity,
        editScope: RecurringEditScope = RecurringEditScope.THIS_OCCURRENCE_ONLY
    ): Long {
        val zone = ZoneId.systemDefault()
        val recomputedEpoch = DateTimeUtils.computeEpochMillis(
            reminder.localDateStr,
            reminder.localTimeStr,
            zone
        )
        val now = System.currentTimeMillis()
        val normalized = reminder.copy(
            timezoneId = zone.id,
            scheduledEpochMillis = recomputedEpoch,
            snoozedUntilEpochMillis = null,
            updatedAtEpochMillis = now
        )

        if (normalized.id == 0L) {
            val newId = dao.insertReminder(normalized.copy(createdAtEpochMillis = now))
            val saved = normalized.copy(id = newId, createdAtEpochMillis = now)
            scheduler.scheduleReminder(saved)
            return newId
        }

        if (editScope == RecurringEditScope.ENTIRE_SERIES && normalized.isRecurring) {
            val seriesItems = dao.getRemindersBySeriesIdSync(normalized.seriesId)
            seriesItems.filter { !it.isCompleted && it.id != normalized.id }.forEach { other ->
                val updatedOtherEpoch = DateTimeUtils.computeEpochMillis(
                    other.localDateStr,
                    normalized.localTimeStr,
                    zone
                )
                val updatedOther = other.copy(
                    title = normalized.title,
                    description = normalized.description,
                    localTimeStr = normalized.localTimeStr,
                    timezoneId = zone.id,
                    scheduledEpochMillis = updatedOtherEpoch,
                    category = normalized.category,
                    colorHex = normalized.colorHex,
                    priority = normalized.priority,
                    recurrenceType = normalized.recurrenceType,
                    recurrenceInterval = normalized.recurrenceInterval,
                    recurrenceWeekdays = normalized.recurrenceWeekdays,
                    recurrenceEndDate = normalized.recurrenceEndDate,
                    recurrenceMaxOccurrences = normalized.recurrenceMaxOccurrences,
                    isRecurrencePaused = normalized.isRecurrencePaused,
                    notificationEnabled = normalized.notificationEnabled,
                    advanceAlertOffsetsMinutes = normalized.advanceAlertOffsetsMinutes,
                    updatedAtEpochMillis = now
                )
                dao.updateReminder(updatedOther)
                scheduler.scheduleReminder(updatedOther)
            }
        }

        dao.updateReminder(normalized)
        scheduler.scheduleReminder(normalized)
        return normalized.id
    }

    suspend fun setReminderCompleted(reminderId: Long, completed: Boolean): ReminderEntity? {
        val existing = dao.getReminderByIdSync(reminderId) ?: return null
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isCompleted = completed,
            completedAtEpochMillis = if (completed) now else null,
            snoozedUntilEpochMillis = null,
            updatedAtEpochMillis = now
        )
        dao.updateReminder(updated)

        if (completed) {
            scheduler.cancelReminderAlarms(reminderId)
            if (existing.isRecurring && !existing.isRecurrencePaused) {
                spawnNextRecurringOccurrenceIfNeeded(existing)
            }
        } else {
            scheduler.scheduleReminder(updated)
        }
        return updated
    }

    private suspend fun spawnNextRecurringOccurrenceIfNeeded(completedReminder: ReminderEntity): ReminderEntity? {
        val seriesItems = dao.getRemindersBySeriesIdSync(completedReminder.seriesId)
        val hasFutureUncompleted = seriesItems.any { !it.isCompleted && it.id != completedReminder.id }
        if (hasFutureUncompleted) return null

        val nextDate = DateTimeUtils.computeNextOccurrenceDate(completedReminder) ?: return null
        val nextDateStr = DateTimeUtils.formatLocalDate(nextDate)
        val zone = ZoneId.systemDefault()
        val nextEpoch = DateTimeUtils.computeEpochMillis(
            nextDateStr,
            completedReminder.localTimeStr,
            zone
        )
        val now = System.currentTimeMillis()
        val nextOccurrence = completedReminder.copy(
            id = 0L,
            localDateStr = nextDateStr,
            timezoneId = zone.id,
            scheduledEpochMillis = nextEpoch,
            occurrenceIndex = completedReminder.occurrenceIndex + 1,
            snoozedUntilEpochMillis = null,
            isCompleted = false,
            completedAtEpochMillis = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val newId = dao.insertReminder(nextOccurrence)
        val savedNext = nextOccurrence.copy(id = newId)
        scheduler.scheduleReminder(savedNext)
        return savedNext
    }

    suspend fun snoozeReminder(reminderId: Long, snoozeMinutes: Int): ReminderEntity? {
        val existing = dao.getReminderByIdSync(reminderId) ?: return null
        val now = System.currentTimeMillis()
        val snoozedUntil = now + (snoozeMinutes.coerceAtLeast(1) * 60_000L)
        val updated = existing.copy(
            snoozedUntilEpochMillis = snoozedUntil,
            isCompleted = false,
            completedAtEpochMillis = null,
            updatedAtEpochMillis = now
        )
        dao.updateReminder(updated)
        scheduler.scheduleReminder(updated, now)
        return updated
    }

    suspend fun setRecurrencePaused(reminderId: Long, paused: Boolean): ReminderEntity? {
        val existing = dao.getReminderByIdSync(reminderId) ?: return null
        val now = System.currentTimeMillis()
        val seriesItems = dao.getRemindersBySeriesIdSync(existing.seriesId)
        var targetUpdated: ReminderEntity? = null
        seriesItems.filter { !it.isCompleted }.forEach { item ->
            val updated = item.copy(
                isRecurrencePaused = paused,
                updatedAtEpochMillis = now
            )
            dao.updateReminder(updated)
            if (paused) {
                scheduler.cancelReminderAlarms(updated.id)
            } else {
                scheduler.scheduleReminder(updated)
            }
            if (item.id == reminderId) targetUpdated = updated
        }
        return targetUpdated
    }

    suspend fun deleteReminder(reminderId: Long, deleteEntireSeries: Boolean = false) {
        val existing = dao.getReminderByIdSync(reminderId) ?: return
        if (deleteEntireSeries && existing.isRecurring) {
            val seriesItems = dao.getRemindersBySeriesIdSync(existing.seriesId)
            seriesItems.forEach { scheduler.cancelReminderAlarms(it.id) }
            dao.deleteEntireSeries(existing.seriesId)
        } else {
            scheduler.cancelReminderAlarms(reminderId)
            dao.deleteReminderById(reminderId)
        }
    }

    suspend fun deleteCompletedReminders() {
        val all = dao.getAllRemindersSync()
        all.filter { it.isCompleted }.forEach { scheduler.cancelReminderAlarms(it.id) }
        dao.deleteCompletedReminders()
    }

    suspend fun reconcileAllRemindersWithSystemClock(
        currentZone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val activeReminders = dao.getActiveUncompletedRemindersSync()
        var scheduledRemindersCount = 0
        val now = System.currentTimeMillis()

        activeReminders.forEach { reminder ->
            val expectedEpoch = DateTimeUtils.computeEpochMillis(
                reminder.localDateStr,
                reminder.localTimeStr,
                currentZone
            )
            val currentReminder = if (
                reminder.timezoneId != currentZone.id ||
                reminder.scheduledEpochMillis != expectedEpoch
            ) {
                val adjusted = reminder.copy(
                    timezoneId = currentZone.id,
                    scheduledEpochMillis = expectedEpoch,
                    updatedAtEpochMillis = now
                )
                dao.updateReminder(adjusted)
                adjusted
            } else {
                reminder
            }

            val triggers = scheduler.scheduleReminder(currentReminder, now)
            if (triggers > 0) {
                scheduledRemindersCount++
            }
        }

        preferencesRepository.recordReconciliation(now, currentZone.id)
        return scheduledRemindersCount
    }

    suspend fun getReminderByIdSync(id: Long): ReminderEntity? = dao.getReminderByIdSync(id)

    /**
     * Exports all reminders, tasks, and user settings into a portable, human-readable JSON backup.
     */
    suspend fun exportRemindersToJson(): String {
        val reminders = dao.getAllRemindersSync()
        val tasks = taskDao?.getAllTasksSync().orEmpty()
        val settings = preferencesRepository.getSettingsSync()

        val root = JSONObject()
        root.put("app", "DayFlow")
        root.put("schemaVersion", 3)
        root.put("exportedAtEpochMillis", System.currentTimeMillis())
        root.put("exportedTimezoneId", ZoneId.systemDefault().id)
        root.put("settings", settings.toJsonObject())

        val remindersArray = JSONArray()
        reminders.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("seriesId", r.seriesId)
                put("title", r.title)
                put("description", r.description)
                put("localDateStr", r.localDateStr)
                put("localTimeStr", r.localTimeStr)
                put("timezoneId", r.timezoneId)
                put("scheduledEpochMillis", r.scheduledEpochMillis)
                put("category", r.category.name)
                put("colorHex", r.colorHex)
                put("priority", r.priority.name)
                put("recurrenceType", r.recurrenceType.name)
                put("recurrenceInterval", r.recurrenceInterval)
                put("recurrenceWeekdays", r.recurrenceWeekdays)
                put("recurrenceEndDate", r.recurrenceEndDate ?: JSONObject.NULL)
                put("recurrenceMaxOccurrences", r.recurrenceMaxOccurrences ?: JSONObject.NULL)
                put("occurrenceIndex", r.occurrenceIndex)
                put("isRecurrencePaused", r.isRecurrencePaused)
                put("notificationEnabled", r.notificationEnabled)
                put("advanceAlertOffsetsMinutes", r.advanceAlertOffsetsMinutes)
                put("isCompleted", r.isCompleted)
                put("completedAtEpochMillis", r.completedAtEpochMillis ?: JSONObject.NULL)
                put("createdAtEpochMillis", r.createdAtEpochMillis)
                put("updatedAtEpochMillis", r.updatedAtEpochMillis)
            }
            remindersArray.put(obj)
        }
        root.put("reminders", remindersArray)

        if (tasks.isNotEmpty()) {
            val tasksArray = JSONArray()
            tasks.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("description", t.description)
                    put("dueDate", t.dueDate)
                    put("category", t.category.name)
                    put("priority", t.priority.name)
                    put("isCompleted", t.isCompleted)
                    put("recurrenceRule", t.recurrenceRule)
                    put("seriesId", t.seriesId)
                    put("localDateStr", t.localDateStr)
                    put("localTimeStr", t.localTimeStr)
                    put("colorHex", t.colorHex)
                }
                tasksArray.put(obj)
            }
            root.put("tasks", tasksArray)
        }

        return root.toString(2)
    }

    /**
     * Imports reminders, tasks, and user settings from a JSON backup string.
     * Returns a detailed [BackupImportSummary].
     */
    suspend fun importBackupFromJson(
        jsonString: String,
        replaceExisting: Boolean = false,
        restoreSettings: Boolean = true
    ): BackupImportSummary {
        val root = JSONObject(jsonString)
        val zone = ZoneId.systemDefault()

        var settingsRestored = false
        if (restoreSettings && root.has("settings")) {
            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                preferencesRepository.restoreSettingsFromJson(settingsObj)
                settingsRestored = true
            }
        }

        if (replaceExisting) {
            val current = dao.getAllRemindersSync()
            current.forEach { scheduler.cancelReminderAlarms(it.id) }
            dao.clearAllReminders()
            taskDao?.clearAllTasks()
        }

        var importedRemindersCount = 0
        val array = root.optJSONArray("reminders")
        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                if (title.isEmpty()) continue

                val localDateStr = obj.optString(
                    "localDateStr",
                    DateTimeUtils.formatLocalDate(LocalDate.now())
                )
                val localTimeStr = obj.optString("localTimeStr", "09:00")
                val category = runCatching {
                    ReminderCategory.valueOf(obj.optString("category", "PERSONAL"))
                }.getOrDefault(ReminderCategory.PERSONAL)
                val priority = runCatching {
                    ReminderPriority.valueOf(obj.optString("priority", "MEDIUM"))
                }.getOrDefault(ReminderPriority.MEDIUM)
                val recurrenceType = runCatching {
                    RecurrenceType.valueOf(obj.optString("recurrenceType", "NONE"))
                }.getOrDefault(RecurrenceType.NONE)

                val endDate = if (obj.isNull("recurrenceEndDate")) null else obj.optString("recurrenceEndDate")
                val maxOcc = if (obj.isNull("recurrenceMaxOccurrences")) null else obj.optInt("recurrenceMaxOccurrences")
                val completedAt = if (obj.isNull("completedAtEpochMillis")) null else obj.optLong("completedAtEpochMillis")

                val entity = ReminderEntity(
                    id = 0L,
                    seriesId = obj.optString("seriesId", UUID.randomUUID().toString()),
                    title = title,
                    description = obj.optString("description", ""),
                    localDateStr = localDateStr,
                    localTimeStr = localTimeStr,
                    timezoneId = zone.id,
                    scheduledEpochMillis = DateTimeUtils.computeEpochMillis(localDateStr, localTimeStr, zone),
                    category = category,
                    colorHex = obj.optString("colorHex", category.defaultColorHex),
                    priority = priority,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = obj.optInt("recurrenceInterval", 1).coerceAtLeast(1),
                    recurrenceWeekdays = obj.optString("recurrenceWeekdays", ""),
                    recurrenceEndDate = endDate?.takeIf { it.isNotBlank() },
                    recurrenceMaxOccurrences = maxOcc?.takeIf { it > 0 },
                    occurrenceIndex = obj.optInt("occurrenceIndex", 1).coerceAtLeast(1),
                    isRecurrencePaused = obj.optBoolean("isRecurrencePaused", false),
                    notificationEnabled = obj.optBoolean("notificationEnabled", true),
                    advanceAlertOffsetsMinutes = obj.optString("advanceAlertOffsetsMinutes", "0"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedAtEpochMillis = completedAt,
                    createdAtEpochMillis = obj.optLong("createdAtEpochMillis", System.currentTimeMillis()),
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
                val id = dao.insertReminder(entity)
                scheduler.scheduleReminder(entity.copy(id = id))
                importedRemindersCount++
            }
        }

        var importedTasksCount = 0
        val tasksArray = root.optJSONArray("tasks")
        if (tasksArray != null && taskDao != null) {
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                if (title.isEmpty()) continue
                val task = Task(
                    id = 0L,
                    title = title,
                    description = obj.optString("description", ""),
                    dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                    category = Task.parseCategory(obj.optString("category", "PERSONAL")),
                    priority = Task.parsePriority(obj.optString("priority", "MEDIUM")),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    recurrenceRule = obj.optString("recurrenceRule", "NONE"),
                    seriesId = obj.optString("seriesId", UUID.randomUUID().toString())
                )
                taskDao.insertTask(task)
                importedTasksCount++
            }
        }

        return BackupImportSummary(
            remindersImported = importedRemindersCount,
            tasksImported = importedTasksCount,
            settingsRestored = settingsRestored
        )
    }

    suspend fun importRemindersFromJson(jsonString: String, replaceExisting: Boolean = false): Int {
        return importBackupFromJson(jsonString, replaceExisting, restoreSettings = true).remindersImported
    }
}

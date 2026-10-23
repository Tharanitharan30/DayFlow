package com.example.data

import com.example.notifications.ReminderScheduler
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting data access for the [Task] Room entity and coordinating
 * native notification scheduling and recurrence rule advancement.
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val scheduler: ReminderScheduler? = null
) {
    val allTasks: Flow<List<Task>> = taskDao.getTasks()

    fun getTasks(): Flow<List<Task>> = taskDao.getTasks()

    fun observeByCompletionStatus(isCompleted: Boolean): Flow<List<Task>> =
        taskDao.observeTasksByCompletionStatus(isCompleted)

    fun observeByCategory(category: ReminderCategory): Flow<List<Task>> =
        taskDao.observeTasksByCategory(category)

    fun observeByPriority(priority: ReminderPriority): Flow<List<Task>> =
        taskDao.observeTasksByPriority(priority)

    fun observeOverdueTasks(nowEpochMillis: Long = System.currentTimeMillis()): Flow<List<Task>> =
        taskDao.observeOverdueTasks(nowEpochMillis)

    fun observeTasksDueBetween(startEpochMillis: Long, endEpochMillis: Long): Flow<List<Task>> =
        taskDao.observeTasksDueBetween(startEpochMillis, endEpochMillis)

    fun searchTasks(query: String): Flow<List<Task>> =
        taskDao.searchTasks(query)

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun getAllTasksSync(): List<Task> = taskDao.getAllTasksSync()

    suspend fun insert(task: Task): Long {
        val now = System.currentTimeMillis()
        val normalized = task.copy(
            recurrenceRule = Task.buildRecurrenceRuleString(
                type = task.recurrenceType,
                interval = task.recurrenceInterval,
                weekdays = task.recurrenceWeekdays,
                endDate = task.recurrenceEndDate,
                maxOccurrences = task.recurrenceMaxOccurrences
            ),
            createdAt = now,
            updatedAt = now
        )
        val id = taskDao.insertTask(normalized)
        val saved = normalized.copy(id = id)
        scheduler?.scheduleReminder(saved.toReminderEntity())
        return id
    }

    suspend fun update(task: Task) {
        val updated = task.copy(
            recurrenceRule = Task.buildRecurrenceRuleString(
                type = task.recurrenceType,
                interval = task.recurrenceInterval,
                weekdays = task.recurrenceWeekdays,
                endDate = task.recurrenceEndDate,
                maxOccurrences = task.recurrenceMaxOccurrences
            ),
            updatedAt = System.currentTimeMillis()
        )
        taskDao.updateTask(updated)
        scheduler?.scheduleReminder(updated.toReminderEntity())
    }

    /**
     * Toggles or updates completion status for a [Task].
     * If a recurring task is completed, spawns the next occurrence according to its recurrence rule.
     */
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean): Task? {
        val existing = taskDao.getTaskById(taskId) ?: return null
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isCompleted = completed,
            completedAt = if (completed) now else null,
            updatedAt = now
        )
        taskDao.updateTask(updated)

        if (completed) {
            scheduler?.cancelReminderAlarms(taskId)
            if (existing.isRecurring && !existing.isRecurrencePaused) {
                spawnNextRecurringTaskIfNeeded(existing)
            }
        } else {
            scheduler?.scheduleReminder(updated.toReminderEntity())
        }
        return updated
    }

    private suspend fun spawnNextRecurringTaskIfNeeded(completedTask: Task): Task? {
        val seriesTasks = taskDao.getTasksBySeriesId(completedTask.seriesId)
        val hasFutureActive = seriesTasks.any { !it.isCompleted && it.id != completedTask.id }
        if (hasFutureActive) return null

        val asReminder = completedTask.toReminderEntity().copy(
            occurrenceIndex = seriesTasks.size
        )
        val nextDate = DateTimeUtils.computeNextOccurrenceDate(asReminder) ?: return null
        val nextDateStr = DateTimeUtils.formatLocalDate(nextDate)
        val nextDueEpoch = DateTimeUtils.computeEpochMillis(
            nextDateStr,
            completedTask.localTimeStr,
            ZoneId.systemDefault()
        )
        val now = System.currentTimeMillis()
        val nextTask = completedTask.copy(
            id = 0L,
            dueDate = nextDueEpoch,
            localDateStr = nextDateStr,
            isCompleted = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now
        )
        val newId = taskDao.insertTask(nextTask)
        val savedNext = nextTask.copy(id = newId)
        scheduler?.scheduleReminder(savedNext.toReminderEntity())
        return savedNext
    }

    suspend fun deleteById(id: Long) {
        scheduler?.cancelReminderAlarms(id)
        taskDao.deleteTaskById(id)
    }

    suspend fun deleteSeries(seriesId: String) {
        val items = taskDao.getTasksBySeriesId(seriesId)
        items.forEach { scheduler?.cancelReminderAlarms(it.id) }
        taskDao.deleteSeriesById(seriesId)
    }

    suspend fun deleteCompleted() {
        val all = taskDao.getAllTasksSync()
        all.filter { it.isCompleted }.forEach { scheduler?.cancelReminderAlarms(it.id) }
        taskDao.deleteCompletedTasks()
    }
}

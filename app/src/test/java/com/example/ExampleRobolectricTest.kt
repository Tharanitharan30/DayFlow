package com.example

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateTimeUtils
import com.example.data.DayFlowDatabase
import com.example.data.RecurrenceType
import com.example.data.RecurringEditScope
import com.example.data.ReminderCategory
import com.example.data.ReminderEntity
import com.example.data.ReminderPriority
import com.example.data.ReminderRepository
import com.example.data.UserPreferencesRepository
import com.example.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: DayFlowDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var repository: ReminderRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = DayFlowDatabase.createInMemory(context)
        scheduler = ReminderScheduler(context)
        prefs = UserPreferencesRepository(context)
        repository = ReminderRepository(db.reminderDao(), scheduler, prefs, db.taskDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app_name is DayFlow`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("DayFlow", appName)
    }

    @Test
    fun `create edit complete snooze and delete one-time reminder`() = runBlocking {
        val tomorrowStr = DateTimeUtils.formatLocalDate(LocalDate.now().plusDays(1))
        val initial = ReminderEntity(
            title = "Submit Q4 Tax Documents",
            description = "Include signed forms",
            localDateStr = tomorrowStr,
            localTimeStr = "14:30",
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(tomorrowStr, "14:30"),
            category = ReminderCategory.FINANCE,
            priority = ReminderPriority.HIGH,
            advanceAlertOffsetsMinutes = "0,15"
        )

        val id = repository.createOrUpdateReminder(initial)
        assertTrue(id > 0L)

        // Verify AlarmManager received scheduled triggers (on-time + 15m advance)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

        // Edit reminder title and disable advance alert
        val saved = repository.getReminderByIdSync(id)
        assertNotNull(saved)
        repository.createOrUpdateReminder(
            saved!!.copy(
                title = "Submit Q4 Tax Documents (Updated)",
                advanceAlertOffsetsMinutes = "0"
            )
        )
        val updated = repository.getReminderByIdSync(id)
        assertEquals("Submit Q4 Tax Documents (Updated)", updated?.title)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        // Snooze reminder for 10 minutes
        val snoozed = repository.snoozeReminder(id, 10)
        assertNotNull(snoozed?.snoozedUntilEpochMillis)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        // Complete reminder -> cancels scheduled alarm
        val completed = repository.setReminderCompleted(id, true)
        assertTrue(completed?.isCompleted == true)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

        // Delete reminder
        repository.deleteReminder(id)
        assertNull(repository.getReminderByIdSync(id))
    }

    @Test
    fun `completing recurring reminder spawns next occurrence without deleting history or duplicating`() = runBlocking {
        val today = LocalDate.now()
        val todayStr = DateTimeUtils.formatLocalDate(today)
        val recurring = ReminderEntity(
            title = "Evening Reading",
            localDateStr = todayStr,
            localTimeStr = "21:00",
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(todayStr, "21:00"),
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            recurrenceMaxOccurrences = 3
        )

        val firstId = repository.createOrUpdateReminder(recurring)
        repository.setReminderCompleted(firstId, true)

        val allItems = db.reminderDao().getAllRemindersSync()
        assertEquals(2, allItems.size)

        val firstItem = allItems.first { it.id == firstId }
        val secondItem = allItems.first { it.id != firstId }

        assertTrue(firstItem.isCompleted)
        assertFalse(secondItem.isCompleted)
        assertEquals(DateTimeUtils.formatLocalDate(today.plusDays(1)), secondItem.localDateStr)
        assertEquals(2, secondItem.occurrenceIndex)
        assertEquals(firstItem.seriesId, secondItem.seriesId)

        // Completing the first occurrence again should not spawn a duplicate second occurrence
        repository.setReminderCompleted(firstId, true)
        assertEquals(2, db.reminderDao().getAllRemindersSync().size)
    }

    @Test
    fun `editing entire recurring series updates all active occurrences and pausing stops alarms`() = runBlocking {
        val today = LocalDate.now()
        val seriesId = "series-test-123"
        val item1 = ReminderEntity(
            seriesId = seriesId,
            title = "Standup Sync",
            localDateStr = DateTimeUtils.formatLocalDate(today.plusDays(1)),
            localTimeStr = "09:00",
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                DateTimeUtils.formatLocalDate(today.plusDays(1)),
                "09:00"
            ),
            recurrenceType = RecurrenceType.DAILY,
            occurrenceIndex = 1
        )
        val item2 = item1.copy(
            localDateStr = DateTimeUtils.formatLocalDate(today.plusDays(2)),
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                DateTimeUtils.formatLocalDate(today.plusDays(2)),
                "09:00"
            ),
            occurrenceIndex = 2
        )

        val id1 = repository.createOrUpdateReminder(item1)
        val id2 = repository.createOrUpdateReminder(item2)

        val saved1 = repository.getReminderByIdSync(id1)!!
        repository.createOrUpdateReminder(
            saved1.copy(title = "Architecture Sync", localTimeStr = "10:30"),
            editScope = RecurringEditScope.ENTIRE_SERIES
        )

        val after2 = repository.getReminderByIdSync(id2)!!
        assertEquals("Architecture Sync", after2.title)
        assertEquals("10:30", after2.localTimeStr)

        // Pause recurring schedule -> cancels alarms
        repository.setRecurrencePaused(id1, true)
        val paused2 = repository.getReminderByIdSync(id2)!!
        assertTrue(paused2.isRecurrencePaused)
    }

    @Test
    fun `timezone reconciliation recalculates epoch while preserving local scheduled date and time`() = runBlocking {
        val futureDateStr = DateTimeUtils.formatLocalDate(LocalDate.now().plusDays(2))
        val item = ReminderEntity(
            title = "Flight Check-In",
            localDateStr = futureDateStr,
            localTimeStr = "11:15",
            timezoneId = "UTC",
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(
                futureDateStr,
                "11:15",
                ZoneId.of("UTC")
            )
        )
        val id = db.reminderDao().insertReminder(item)

        val tokyoZone = ZoneId.of("Asia/Tokyo")
        repository.reconcileAllRemindersWithSystemClock(tokyoZone)

        val reconciled = repository.getReminderByIdSync(id)!!
        assertEquals("11:15", reconciled.localTimeStr)
        assertEquals(futureDateStr, reconciled.localDateStr)
        assertEquals("Asia/Tokyo", reconciled.timezoneId)
        assertEquals(
            DateTimeUtils.computeEpochMillis(futureDateStr, "11:15", tokyoZone),
            reconciled.scheduledEpochMillis
        )
    }

    @Test
    fun `json export and import preserves reminders tasks and user settings`() = runBlocking {
        val dateStr = DateTimeUtils.formatLocalDate(LocalDate.now().plusDays(1))
        prefs.setThemeMode(com.example.data.ThemeMode.DARK)
        prefs.setUse24HourFormat(true)
        prefs.setDefaultAdvanceMinutes(15)

        repository.createOrUpdateReminder(
            ReminderEntity(
                title = "Backup Verification Task",
                description = "Verify JSON roundtrip",
                localDateStr = dateStr,
                localTimeStr = "16:45",
                scheduledEpochMillis = DateTimeUtils.computeEpochMillis(dateStr, "16:45"),
                category = ReminderCategory.WORK,
                priority = ReminderPriority.HIGH,
                recurrenceType = RecurrenceType.SELECTED_WEEKDAYS,
                recurrenceWeekdays = "1,3,5",
                advanceAlertOffsetsMinutes = "0,10,30"
            )
        )

        val exportedJson = repository.exportRemindersToJson()
        assertTrue(exportedJson.contains("Backup Verification Task"))
        assertTrue(exportedJson.contains("\"settings\""))

        // Change settings before restoring to verify restoreSettingsFromJson overwrites them
        prefs.setThemeMode(com.example.data.ThemeMode.LIGHT)
        prefs.setUse24HourFormat(false)
        prefs.setDefaultAdvanceMinutes(5)

        val backupSummary = repository.importBackupFromJson(
            jsonString = exportedJson,
            replaceExisting = true,
            restoreSettings = true
        )
        assertEquals(1, backupSummary.remindersImported)
        assertTrue(backupSummary.settingsRestored)

        val restoredSettings = prefs.getSettingsSync()
        assertEquals(com.example.data.ThemeMode.DARK, restoredSettings.themeMode)
        assertTrue(restoredSettings.use24HourFormat)
        assertEquals(15, restoredSettings.defaultAdvanceMinutes)

        val all = db.reminderDao().getAllRemindersSync()
        assertEquals(1, all.size)
        assertEquals("Backup Verification Task", all.first().title)
        assertEquals(RecurrenceType.SELECTED_WEEKDAYS, all.first().recurrenceType)
        assertEquals("1,3,5", all.first().recurrenceWeekdays)
        assertEquals(listOf(0, 10, 30), all.first().parsedAdvanceOffsets())

        // Verify NotificationDiagnostics snapshot
        val diagnostics = scheduler.getDiagnostics(all)
        assertEquals(1, diagnostics.activeScheduledRemindersCount)
        assertTrue(diagnostics.deviceManufacturer.isNotBlank())
        assertTrue(diagnostics.oemPowerGuidance.isNotBlank())
    }

    @Test
    fun `Task entity TaskDao and TaskRepository persist fields filters and recurrence rules`() = runBlocking {
        val taskDao = db.taskDao()
        val taskRepo = com.example.data.TaskRepository(taskDao, scheduler)

        val dueTomorrow = LocalDate.now().plusDays(1)
        val task = com.example.data.Task.createForLocalDateTime(
            title = "Prepare Architecture Review Deck",
            description = "Include Room database schema diagrams",
            localDate = dueTomorrow,
            category = ReminderCategory.WORK,
            priority = ReminderPriority.HIGH,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 1,
            recurrenceWeekdays = "1,4",
            recurrenceMaxOccurrences = 5
        )

        val insertedId = taskRepo.insert(task)
        assertTrue(insertedId > 0L)

        val loaded = taskRepo.getTaskById(insertedId)
        assertNotNull(loaded)
        assertEquals("Prepare Architecture Review Deck", loaded?.title)
        assertEquals("Include Room database schema diagrams", loaded?.description)
        assertEquals(ReminderCategory.WORK, loaded?.category)
        assertEquals(ReminderPriority.HIGH, loaded?.priority)
        assertFalse(loaded?.isCompleted == true)
        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=1,4;COUNT=5", loaded?.recurrenceRule)

        // Complete the recurring task -> marks current completed and spawns next weekly occurrence
        taskRepo.setTaskCompleted(insertedId, true)
        val allTasks = taskRepo.getAllTasksSync()
        assertEquals(2, allTasks.size)
        assertTrue(allTasks.first { it.id == insertedId }.isCompleted)
        val nextOccurrence = allTasks.first { it.id != insertedId }
        assertFalse(nextOccurrence.isCompleted)
        assertEquals(DateTimeUtils.formatLocalDate(dueTomorrow.plusWeeks(1)), nextOccurrence.localDateStr)

        // Verify direct positional / named construction of Task with core fields
        val directTask = com.example.data.Task(
            id = 0L,
            title = "Direct Task Entity",
            description = "Verify @Entity fields directly",
            dueDate = 1761120000000L,
            category = ReminderCategory.HEALTH,
            priority = ReminderPriority.MEDIUM,
            isCompleted = false,
            recurrenceRule = "FREQ=DAILY;INTERVAL=1"
        )
        val directId = taskDao.insertTask(directTask)
        val fetchedDirect = taskDao.getTaskById(directId)
        assertNotNull(fetchedDirect)
        assertEquals("Direct Task Entity", fetchedDirect?.title)
        assertEquals(1761120000000L, fetchedDirect?.dueDate)
        assertEquals("FREQ=DAILY;INTERVAL=1", fetchedDirect?.recurrenceRule)

        // Verify getTasks() returns tasks sorted by dueDate ascending
        val earlierTask = com.example.data.Task(
            title = "Earlier Due Task",
            description = "Should appear first in getTasks()",
            dueDate = 1000000000000L,
            category = ReminderCategory.PERSONAL,
            priority = ReminderPriority.LOW,
            isCompleted = false,
            recurrenceRule = "NONE"
        )
        val earlierId = taskDao.insertTask(earlierTask)
        val sortedList = taskDao.getTasks().first()
        assertEquals("Earlier Due Task", sortedList.first().title)

        // Verify updateTask and deleteTask
        val updatedEarlier = sortedList.first().copy(title = "Updated Earlier Task", isCompleted = true)
        taskDao.updateTask(updatedEarlier)
        assertEquals("Updated Earlier Task", taskDao.getTaskById(earlierId)?.title)
        assertTrue(taskDao.getTaskById(earlierId)?.isCompleted == true)

        taskDao.deleteTask(updatedEarlier)
        assertNull(taskDao.getTaskById(earlierId))
    }

    @Test
    fun `task with isRingingAlarm schedules AlarmClockInfo and supports snooze and dismiss`() = runBlocking {
        val futureDateStr = DateTimeUtils.formatLocalDate(LocalDate.now().plusDays(1))
        val ringingReminder = ReminderEntity(
            title = "Morning Medicine Alarm",
            description = "Take prescribed dose with water",
            localDateStr = futureDateStr,
            localTimeStr = "08:00",
            scheduledEpochMillis = DateTimeUtils.computeEpochMillis(futureDateStr, "08:00"),
            isRingingAlarm = true,
            alarmVibration = true,
            snoozeDurationMinutes = 10,
            advanceAlertOffsetsMinutes = "0"
        )

        val id = repository.createOrUpdateReminder(ringingReminder)
        assertTrue(id > 0L)

        val saved = repository.getReminderByIdSync(id)
        assertNotNull(saved)
        assertTrue(saved!!.isRingingAlarm)
        assertTrue(saved.alarmVibration)
        assertEquals(10, saved.snoozeDurationMinutes)

        // Verify AlarmManager received an alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        val scheduledAlarm = shadowAlarmManager.scheduledAlarms.first()
        assertNotNull("Ringing alarm should be scheduled with AlarmClockInfo", scheduledAlarm.alarmClockInfo)

        // Snooze the alarm for 10 minutes
        val snoozed = repository.snoozeReminder(id, 10)
        assertNotNull(snoozed)
        assertNotNull(snoozed?.snoozedUntilEpochMillis)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        val snoozedAlarm = shadowAlarmManager.scheduledAlarms.first()
        assertNotNull("Snoozed ringing alarm should also use AlarmClockInfo", snoozedAlarm.alarmClockInfo)

        // Completing or deleting cancels scheduled alarm
        repository.setReminderCompleted(id, true)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `alarm history tracks triggered alarms actual vs scheduled times and limits to last 10`() = runBlocking {
        val historyDao = db.alarmHistoryDao()
        val now = System.currentTimeMillis()

        // Insert 12 alarm firing records with varying delays (on-time, minor, severe)
        for (i in 1..12) {
            val scheduled = now + (i * 60_000L)
            val delaySeconds = when {
                i % 3 == 0 -> 240L // severe (> 3m)
                i % 2 == 0 -> 45L  // minor (< 3m)
                else -> 2L         // on-time (<= 15s)
            }
            val actual = scheduled + (delaySeconds * 1000L)
            historyDao.insert(
                com.example.data.AlarmHistoryEntity(
                    reminderId = i.toLong(),
                    taskTitle = "Task Trigger #$i",
                    scheduledEpochMillis = scheduled,
                    actualTriggerEpochMillis = actual,
                    delaySeconds = delaySeconds,
                    isRinging = i % 2 == 0,
                    triggerSource = "ALARM_CLOCK"
                )
            )
        }

        // Prune to last 10 entries
        historyDao.pruneToLastTen()
        val recentList = historyDao.getRecentHistorySync()
        assertEquals(10, recentList.size)

        // Most recent should have the highest actualTriggerEpochMillis
        val latest = recentList.first()
        assertEquals("Task Trigger #12", latest.taskTitle)
        assertEquals(240L, latest.delaySeconds)
        assertEquals(com.example.data.DelaySeverity.SEVERE, latest.delaySeverity)

        // Check severe delay categorization
        val severeItem = recentList.first { it.delaySeconds == 240L }
        assertEquals(com.example.data.DelaySeverity.SEVERE, severeItem.delaySeverity)

        // Check on-time delay categorization
        val onTimeItem = recentList.first { it.delaySeconds == 2L }
        assertEquals(com.example.data.DelaySeverity.ON_TIME, onTimeItem.delaySeverity)

        // Clear history
        historyDao.clearAllHistory()
        assertEquals(0, historyDao.getRecentHistorySync().size)
    }
}

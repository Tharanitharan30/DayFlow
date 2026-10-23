package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Room Database Entity representing a Task with fields for:
 * - [id] (auto-generated primary key)
 * - [title]
 * - [description]
 * - [dueDate] (Long epoch timestamp)
 * - [category] (Enum [ReminderCategory])
 * - [priority] (Enum [ReminderPriority])
 * - [isCompleted] (Boolean)
 * - [recurrenceRule] (String)
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["due_date"]),
        Index(value = ["category"]),
        Index(value = ["priority"]),
        Index(value = ["is_completed"]),
        Index(value = ["series_id"])
    ]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    /** Due date stored as Long epoch milliseconds timestamp */
    @ColumnInfo(name = "due_date")
    val dueDate: Long,

    @ColumnInfo(name = "category")
    val category: ReminderCategory = ReminderCategory.PERSONAL,

    @ColumnInfo(name = "priority")
    val priority: ReminderPriority = ReminderPriority.MEDIUM,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    /** Canonical recurrence rule string (e.g., "NONE", "DAILY", "FREQ=WEEKLY;INTERVAL=1;BYDAY=1,4") */
    @ColumnInfo(name = "recurrence_rule")
    val recurrenceRule: String = "NONE",

    @ColumnInfo(name = "series_id")
    val seriesId: String = UUID.randomUUID().toString(),

    /** Local ISO date string (YYYY-MM-DD) for timezone-resilient scheduling */
    @ColumnInfo(name = "local_date_str")
    val localDateStr: String = Instant.ofEpochMilli(dueDate)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .let { DateTimeUtils.formatLocalDate(it) },

    /** Local ISO time string (HH:mm) for timezone-resilient scheduling */
    @ColumnInfo(name = "local_time_str")
    val localTimeStr: String = Instant.ofEpochMilli(dueDate)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .let { DateTimeUtils.formatLocalTime(it) },

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    /** Structured recurrence rule type */
    @ColumnInfo(name = "recurrence_type")
    val recurrenceType: RecurrenceType = parseRecurrenceTypeFromRule(recurrenceRule),

    /** Interval step for recurring rules (e.g., every 1 day, every 2 weeks) */
    @ColumnInfo(name = "recurrence_interval")
    val recurrenceInterval: Int = 1,

    /** Comma-separated ISO weekday numbers (1=Mon..7=Sun) when recurrenceType == SELECTED_WEEKDAYS */
    @ColumnInfo(name = "recurrence_weekdays")
    val recurrenceWeekdays: String = "",

    /** Optional ISO end date (YYYY-MM-DD) for the recurrence series */
    @ColumnInfo(name = "recurrence_end_date")
    val recurrenceEndDate: String? = null,

    /** Optional max number of occurrences for the recurrence series */
    @ColumnInfo(name = "recurrence_max_occurrences")
    val recurrenceMaxOccurrences: Int? = null,

    @ColumnInfo(name = "is_recurrence_paused")
    val isRecurrencePaused: Boolean = false,

    @ColumnInfo(name = "notification_enabled")
    val notificationEnabled: Boolean = true,

    @ColumnInfo(name = "color_hex")
    val colorHex: String = category.defaultColorHex,

    @ColumnInfo(name = "is_ringing_alarm")
    val isRingingAlarm: Boolean = false,

    @ColumnInfo(name = "alarm_sound_uri")
    val alarmSoundUri: String = "system",

    @ColumnInfo(name = "alarm_vibration")
    val alarmVibration: Boolean = true,

    @ColumnInfo(name = "snooze_duration_minutes")
    val snoozeDurationMinutes: Int = 10,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convenience secondary constructor allowing [category] and [priority] to be passed as [String]s.
     */
    @androidx.room.Ignore
    constructor(
        id: Long = 0L,
        title: String,
        description: String = "",
        dueDate: Long,
        category: String,
        priority: String,
        isCompleted: Boolean = false,
        recurrenceRule: String = "NONE"
    ) : this(
        id = id,
        title = title,
        description = description,
        dueDate = dueDate,
        category = parseCategory(category),
        priority = parsePriority(priority),
        isCompleted = isCompleted,
        recurrenceRule = recurrenceRule
    )

    val isRecurring: Boolean
        get() = recurrenceType != RecurrenceType.NONE ||
            (recurrenceRule.isNotBlank() && !recurrenceRule.equals("NONE", ignoreCase = true))

    fun toReminderEntity(): ReminderEntity {
        return ReminderEntity(
            id = id,
            seriesId = seriesId,
            title = title,
            description = description,
            localDateStr = localDateStr,
            localTimeStr = localTimeStr,
            scheduledEpochMillis = dueDate,
            category = category,
            colorHex = colorHex,
            priority = priority,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceWeekdays = recurrenceWeekdays,
            recurrenceEndDate = recurrenceEndDate,
            recurrenceMaxOccurrences = recurrenceMaxOccurrences,
            isRecurrencePaused = isRecurrencePaused,
            notificationEnabled = notificationEnabled,
            isRingingAlarm = isRingingAlarm,
            alarmSoundUri = alarmSoundUri,
            alarmVibration = alarmVibration,
            snoozeDurationMinutes = snoozeDurationMinutes,
            isCompleted = isCompleted,
            completedAtEpochMillis = completedAt,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt
        )
    }

    companion object {
        fun parseCategory(value: String): ReminderCategory =
            try {
                ReminderCategory.valueOf(value.trim().uppercase())
            } catch (_: Exception) {
                ReminderCategory.PERSONAL
            }

        fun parsePriority(value: String): ReminderPriority =
            try {
                ReminderPriority.valueOf(value.trim().uppercase())
            } catch (_: Exception) {
                ReminderPriority.MEDIUM
            }

        fun parseRecurrenceTypeFromRule(rule: String): RecurrenceType {
            val upper = rule.trim().uppercase()
            return when {
                upper.isEmpty() || upper == "NONE" -> RecurrenceType.NONE
                upper.contains("BYDAY=") || upper == "SELECTED_WEEKDAYS" -> RecurrenceType.SELECTED_WEEKDAYS
                upper.contains("FREQ=DAILY") || upper == "DAILY" -> RecurrenceType.DAILY
                upper.contains("FREQ=WEEKLY") || upper == "WEEKLY" -> RecurrenceType.WEEKLY
                upper.contains("FREQ=MONTHLY") || upper == "MONTHLY" -> RecurrenceType.MONTHLY
                upper.contains("FREQ=YEARLY") || upper == "YEARLY" -> RecurrenceType.YEARLY
                upper.contains("CUSTOM") -> RecurrenceType.CUSTOM_DAYS
                else -> RecurrenceType.NONE
            }
        }

        fun buildRecurrenceRuleString(
            type: RecurrenceType,
            interval: Int = 1,
            weekdays: String = "",
            endDate: String? = null,
            maxOccurrences: Int? = null
        ): String {
            if (type == RecurrenceType.NONE) return "NONE"
            val parts = mutableListOf<String>()
            val freq = when (type) {
                RecurrenceType.DAILY, RecurrenceType.CUSTOM_DAYS -> "DAILY"
                RecurrenceType.SELECTED_WEEKDAYS, RecurrenceType.WEEKLY -> "WEEKLY"
                RecurrenceType.MONTHLY -> "MONTHLY"
                RecurrenceType.YEARLY -> "YEARLY"
                RecurrenceType.NONE -> "NONE"
            }
            parts.add("FREQ=$freq")
            parts.add("INTERVAL=${interval.coerceAtLeast(1)}")
            if (weekdays.isNotBlank()) {
                parts.add("BYDAY=$weekdays")
            }
            if (!endDate.isNullOrBlank()) {
                parts.add("UNTIL=$endDate")
            }
            if (maxOccurrences != null && maxOccurrences > 0) {
                parts.add("COUNT=$maxOccurrences")
            }
            return parts.joinToString(";")
        }

        fun fromReminderEntity(entity: ReminderEntity): Task {
            val ruleStr = buildRecurrenceRuleString(
                type = entity.recurrenceType,
                interval = entity.recurrenceInterval,
                weekdays = entity.recurrenceWeekdays,
                endDate = entity.recurrenceEndDate,
                maxOccurrences = entity.recurrenceMaxOccurrences
            )
            return Task(
                id = entity.id,
                title = entity.title,
                description = entity.description,
                dueDate = entity.scheduledEpochMillis,
                category = entity.category,
                priority = entity.priority,
                isCompleted = entity.isCompleted,
                recurrenceRule = ruleStr,
                seriesId = entity.seriesId,
                localDateStr = entity.localDateStr,
                localTimeStr = entity.localTimeStr,
                completedAt = entity.completedAtEpochMillis,
                recurrenceType = entity.recurrenceType,
                recurrenceInterval = entity.recurrenceInterval,
                recurrenceWeekdays = entity.recurrenceWeekdays,
                recurrenceEndDate = entity.recurrenceEndDate,
                recurrenceMaxOccurrences = entity.recurrenceMaxOccurrences,
                isRecurrencePaused = entity.isRecurrencePaused,
                notificationEnabled = entity.notificationEnabled,
                colorHex = entity.colorHex,
                isRingingAlarm = entity.isRingingAlarm,
                alarmSoundUri = entity.alarmSoundUri,
                alarmVibration = entity.alarmVibration,
                snoozeDurationMinutes = entity.snoozeDurationMinutes,
                createdAt = entity.createdAtEpochMillis,
                updatedAt = entity.updatedAtEpochMillis
            )
        }

        fun createForLocalDateTime(
            title: String,
            description: String = "",
            localDate: LocalDate = LocalDate.now(),
            localTime: LocalTime = LocalTime.of(9, 0),
            category: ReminderCategory = ReminderCategory.PERSONAL,
            priority: ReminderPriority = ReminderPriority.MEDIUM,
            recurrenceType: RecurrenceType = RecurrenceType.NONE,
            recurrenceInterval: Int = 1,
            recurrenceWeekdays: String = "",
            recurrenceEndDate: String? = null,
            recurrenceMaxOccurrences: Int? = null
        ): Task {
            val dateStr = DateTimeUtils.formatLocalDate(localDate)
            val timeStr = DateTimeUtils.formatLocalTime(localTime)
            val epoch = DateTimeUtils.computeEpochMillis(dateStr, timeStr)
            val rule = buildRecurrenceRuleString(
                type = recurrenceType,
                interval = recurrenceInterval,
                weekdays = recurrenceWeekdays,
                endDate = recurrenceEndDate,
                maxOccurrences = recurrenceMaxOccurrences
            )
            return Task(
                title = title,
                description = description,
                dueDate = epoch,
                category = category,
                priority = priority,
                isCompleted = false,
                recurrenceRule = rule,
                localDateStr = dateStr,
                localTimeStr = timeStr,
                recurrenceType = recurrenceType,
                recurrenceInterval = recurrenceInterval,
                recurrenceWeekdays = recurrenceWeekdays,
                recurrenceEndDate = recurrenceEndDate,
                recurrenceMaxOccurrences = recurrenceMaxOccurrences
            )
        }
    }
}

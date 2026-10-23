package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class ReminderCategory(val label: String, val defaultColorHex: String) {
    WORK("Work & Deep Focus", "#0A6670"),
    PERSONAL("Personal & Life", "#4338CA"),
    HEALTH("Health & Wellness", "#10B981"),
    FINANCE("Bills & Finance", "#D97706"),
    ERRANDS("Errands & Shopping", "#EC4899"),
    HABIT("Daily Ritual", "#8B5CF6")
}

enum class ReminderPriority(val label: String, val sortOrder: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1)
}

enum class RecurrenceType(val label: String) {
    NONE("One-time"),
    DAILY("Every day"),
    SELECTED_WEEKDAYS("Selected weekdays"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM_DAYS("Custom interval")
}

enum class RecurringEditScope {
    THIS_OCCURRENCE_ONLY,
    ENTIRE_SERIES
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["scheduledEpochMillis"]),
        Index(value = ["seriesId"]),
        Index(value = ["isCompleted"]),
        Index(value = ["localDateStr"]),
        Index(value = ["category"]),
        Index(value = ["priority"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seriesId: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    /** Local ISO date (YYYY-MM-DD) kept distinct from epoch timestamp for timezone resilience */
    val localDateStr: String,
    /** Local ISO time (HH:mm) kept distinct from epoch timestamp for timezone resilience */
    val localTimeStr: String,
    /** Timezone ID active when the reminder was last calculated */
    val timezoneId: String = ZoneId.systemDefault().id,
    /** Computed epoch milliseconds corresponding to localDateStr + localTimeStr in timezoneId */
    val scheduledEpochMillis: Long,
    val category: ReminderCategory = ReminderCategory.PERSONAL,
    val colorHex: String = category.defaultColorHex,
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 1,
    /** Comma-separated ISO DayOfWeek numbers (1=Mon .. 7=Sun), e.g., "1,3,5" */
    val recurrenceWeekdays: String = "",
    /** Optional end date in YYYY-MM-DD format */
    val recurrenceEndDate: String? = null,
    /** Optional max number of occurrences in the series */
    val recurrenceMaxOccurrences: Int? = null,
    /** 1-based index of this occurrence within its series */
    val occurrenceIndex: Int = 1,
    /** Allows pausing a recurring schedule without deleting it */
    val isRecurrencePaused: Boolean = false,
    val notificationEnabled: Boolean = true,
    /** Comma-separated advance alert offsets in minutes, e.g., "0,10,60" (0 = at scheduled time) */
    val advanceAlertOffsetsMinutes: String = "0",
    /** Optional snooze override timestamp in epoch millis */
    val snoozedUntilEpochMillis: Long? = null,
    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val isRingingAlarm: Boolean = false,
    val alarmSoundUri: String = "system",
    val alarmVibration: Boolean = true,
    val snoozeDurationMinutes: Int = 10,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    val isRecurring: Boolean
        get() = recurrenceType != RecurrenceType.NONE

    fun parsedAdvanceOffsets(): List<Int> {
        return advanceAlertOffsetsMinutes
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 0 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(0) }
    }

    fun parsedWeekdays(): Set<Int> {
        return recurrenceWeekdays
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
    }

    fun effectiveTriggerMillis(): Long {
        return snoozedUntilEpochMillis ?: scheduledEpochMillis
    }
}

class RoomTypeConverters {
    @TypeConverter
    fun fromCategory(value: ReminderCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): ReminderCategory =
        runCatching { ReminderCategory.valueOf(value) }.getOrDefault(ReminderCategory.PERSONAL)

    @TypeConverter
    fun fromPriority(value: ReminderPriority): String = value.name

    @TypeConverter
    fun toPriority(value: String): ReminderPriority =
        runCatching { ReminderPriority.valueOf(value) }.getOrDefault(ReminderPriority.MEDIUM)

    @TypeConverter
    fun fromRecurrence(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): RecurrenceType =
        runCatching { RecurrenceType.valueOf(value) }.getOrDefault(RecurrenceType.NONE)
}

object DateTimeUtils {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val timeFormatter12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val friendlyDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val fullHeaderDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    fun parseLocalDate(dateStr: String): LocalDate {
        return runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrElse { LocalDate.now() }
    }

    fun parseLocalTime(timeStr: String): LocalTime {
        return runCatching { LocalTime.parse(timeStr, timeFormatter24) }.getOrElse { LocalTime.of(9, 0) }
    }

    fun formatLocalDate(date: LocalDate): String = date.format(dateFormatter)

    fun formatLocalTime(time: LocalTime): String = time.format(timeFormatter24)

    fun computeEpochMillis(
        localDateStr: String,
        localTimeStr: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = parseLocalDate(localDateStr)
        val time = parseLocalTime(localTimeStr)
        return LocalDateTime.of(date, time).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun formatDisplayTime(localTimeStr: String, use24Hour: Boolean): String {
        val time = parseLocalTime(localTimeStr)
        return if (use24Hour) {
            time.format(timeFormatter24)
        } else {
            time.format(timeFormatter12)
        }
    }

    fun formatEpochTime(epochMillis: Long, use24Hour: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
        return if (use24Hour) time.format(timeFormatter24) else time.format(timeFormatter12)
    }

    fun formatEpochDateTimeWithSeconds(
        epochMillis: Long,
        use24Hour: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val dt = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
        val pattern = if (use24Hour) "MMM d, HH:mm:ss" else "MMM d, h:mm:ss a"
        return dt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun formatFriendlyDate(localDateStr: String, today: LocalDate = LocalDate.now()): String {
        val date = parseLocalDate(localDateStr)
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(friendlyDateFormatter)
        }
    }

    fun formatFullHeaderDate(date: LocalDate = LocalDate.now()): String {
        return date.format(fullHeaderDateFormatter)
    }

    /**
     * Computes the next occurrence date for a recurring reminder after [currentDateStr].
     * Returns null if the reminder does not recur or has reached its end date or max occurrences.
     */
    fun computeNextOccurrenceDate(reminder: ReminderEntity): LocalDate? {
        if (!reminder.isRecurring) return null

        val nextOccurrenceNumber = reminder.occurrenceIndex + 1
        val maxOccurrences = reminder.recurrenceMaxOccurrences
        if (maxOccurrences != null && maxOccurrences > 0 && nextOccurrenceNumber > maxOccurrences) {
            return null
        }

        val currentDate = parseLocalDate(reminder.localDateStr)
        val interval = reminder.recurrenceInterval.coerceAtLeast(1).toLong()

        val candidateDate: LocalDate = when (reminder.recurrenceType) {
            RecurrenceType.NONE -> return null
            RecurrenceType.DAILY -> currentDate.plusDays(1)
            RecurrenceType.CUSTOM_DAYS -> currentDate.plusDays(interval)
            RecurrenceType.WEEKLY -> currentDate.plusWeeks(interval)
            RecurrenceType.MONTHLY -> currentDate.plusMonths(interval)
            RecurrenceType.YEARLY -> currentDate.plusYears(interval)
            RecurrenceType.SELECTED_WEEKDAYS -> {
                val weekdays = reminder.parsedWeekdays().ifEmpty { setOf(currentDate.dayOfWeek.value) }
                var probe = currentDate.plusDays(1)
                var daysChecked = 0
                while (probe.dayOfWeek.value !in weekdays && daysChecked < 14) {
                    probe = probe.plusDays(1)
                    daysChecked++
                }
                probe
            }
        }

        val endDateStr = reminder.recurrenceEndDate
        if (!endDateStr.isNullOrBlank()) {
            val endDate = runCatching { LocalDate.parse(endDateStr, dateFormatter) }.getOrNull()
            if (endDate != null && candidateDate.isAfter(endDate)) {
                return null
            }
        }

        return candidateDate
    }

    fun formatRecurrenceSummary(reminder: ReminderEntity): String {
        if (!reminder.isRecurring) return "One-time"
        val base = when (reminder.recurrenceType) {
            RecurrenceType.NONE -> "One-time"
            RecurrenceType.DAILY -> "Every day"
            RecurrenceType.WEEKLY -> if (reminder.recurrenceInterval > 1) {
                "Every ${reminder.recurrenceInterval} weeks"
            } else {
                "Weekly"
            }
            RecurrenceType.MONTHLY -> if (reminder.recurrenceInterval > 1) {
                "Every ${reminder.recurrenceInterval} months"
            } else {
                "Monthly"
            }
            RecurrenceType.YEARLY -> "Yearly"
            RecurrenceType.CUSTOM_DAYS -> "Every ${reminder.recurrenceInterval} days"
            RecurrenceType.SELECTED_WEEKDAYS -> {
                val dayNames = reminder.parsedWeekdays().sorted().map {
                    when (it) {
                        DayOfWeek.MONDAY.value -> "Mon"
                        DayOfWeek.TUESDAY.value -> "Tue"
                        DayOfWeek.WEDNESDAY.value -> "Wed"
                        DayOfWeek.THURSDAY.value -> "Thu"
                        DayOfWeek.FRIDAY.value -> "Fri"
                        DayOfWeek.SATURDAY.value -> "Sat"
                        DayOfWeek.SUNDAY.value -> "Sun"
                        else -> ""
                    }
                }.filter { it.isNotEmpty() }
                if (dayNames.isEmpty()) "Weekdays" else dayNames.joinToString(", ")
            }
        }
        val suffix = buildList {
            if (reminder.isRecurrencePaused) add("Paused")
            if (!reminder.recurrenceEndDate.isNullOrBlank()) add("until ${reminder.recurrenceEndDate}")
            if (reminder.recurrenceMaxOccurrences != null) {
                add("${reminder.occurrenceIndex}/${reminder.recurrenceMaxOccurrences}")
            }
        }
        return if (suffix.isEmpty()) base else "$base (${suffix.joinToString(" • ")})"
    }
}

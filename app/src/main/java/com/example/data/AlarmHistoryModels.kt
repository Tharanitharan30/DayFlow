package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class DelaySeverity(val label: String) {
    ON_TIME("On Time"),
    MINOR("Minor Delay"),
    SEVERE("Battery Delay")
}

@Entity(
    tableName = "alarm_history",
    indices = [
        Index(value = ["actual_trigger_epoch_millis"], name = "index_alarm_history_trigger_time")
    ]
)
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "reminder_id")
    val reminderId: Long = 0L,
    @ColumnInfo(name = "task_title")
    val taskTitle: String,
    @ColumnInfo(name = "scheduled_epoch_millis")
    val scheduledEpochMillis: Long,
    @ColumnInfo(name = "actual_trigger_epoch_millis")
    val actualTriggerEpochMillis: Long,
    @ColumnInfo(name = "delay_seconds")
    val delaySeconds: Long,
    @ColumnInfo(name = "is_ringing")
    val isRinging: Boolean = true,
    @ColumnInfo(name = "trigger_source")
    val triggerSource: String = "ALARM_CLOCK"
) {
    /**
     * Categorizes delay for battery optimization diagnostics:
     * - ON_TIME: <= 15s (nominal Android alarm delivery)
     * - MINOR: <= 180s (slight Doze maintenance window skew)
     * - SEVERE: > 180s (aggressive OEM battery restriction or heavy Doze deferral)
     */
    val delaySeverity: DelaySeverity
        get() = when {
            delaySeconds <= 15L -> DelaySeverity.ON_TIME
            delaySeconds <= 180L -> DelaySeverity.MINOR
            else -> DelaySeverity.SEVERE
        }
}

@Dao
interface AlarmHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AlarmHistoryEntity): Long

    @Query("SELECT * FROM alarm_history ORDER BY actual_trigger_epoch_millis DESC LIMIT 10")
    fun getRecentHistoryFlow(): Flow<List<AlarmHistoryEntity>>

    @Query("SELECT * FROM alarm_history ORDER BY actual_trigger_epoch_millis DESC LIMIT 10")
    suspend fun getRecentHistorySync(): List<AlarmHistoryEntity>

    @Query("DELETE FROM alarm_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM alarm_history WHERE id NOT IN (SELECT id FROM (SELECT id FROM alarm_history ORDER BY actual_trigger_epoch_millis DESC LIMIT 10))")
    suspend fun pruneToLastTen()
}

package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, scheduledEpochMillis ASC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE category = :category ORDER BY isCompleted ASC, scheduledEpochMillis ASC")
    fun observeRemindersByCategory(category: ReminderCategory): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE category IN (:categories) ORDER BY isCompleted ASC, scheduledEpochMillis ASC")
    fun observeRemindersByCategories(categories: List<ReminderCategory>): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE LOWER(customCategoryLabel) = LOWER(:label) ORDER BY isCompleted ASC, scheduledEpochMillis ASC")
    fun observeRemindersByCustomLabel(label: String): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET customCategoryLabel = '' WHERE LOWER(customCategoryLabel) = LOWER(:label)")
    suspend fun clearCustomCategoryLabel(label: String)

    @Query("SELECT * FROM reminders ORDER BY scheduledEpochMillis ASC")
    suspend fun getAllRemindersSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY scheduledEpochMillis ASC")
    suspend fun getActiveUncompletedRemindersSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderByIdSync(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE seriesId = :seriesId ORDER BY occurrenceIndex ASC")
    suspend fun getRemindersBySeriesIdSync(seriesId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>): List<Long>

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders WHERE seriesId = :seriesId AND isCompleted = 0")
    suspend fun deleteFutureSeriesOccurrences(seriesId: String)

    @Query("DELETE FROM reminders WHERE seriesId = :seriesId")
    suspend fun deleteEntireSeries(seriesId: String)

    @Query("DELETE FROM reminders WHERE isCompleted = 1")
    suspend fun deleteCompletedReminders()

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun getReminderCountSync(): Int
}

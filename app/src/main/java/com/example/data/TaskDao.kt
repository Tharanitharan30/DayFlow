package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the [Task] Room entity.
 * Provides standard CRUD operations ([insertTask], [updateTask], [deleteTask], [getTasks])
 * with reactive [Flow] queries sorted by due date and suspend functions for background writes.
 */
@Dao
interface TaskDao {

    /**
     * Reactive query returning all tasks sorted by due date ascending.
     */
    @Query("SELECT * FROM tasks ORDER BY due_date ASC")
    fun getTasks(): Flow<List<Task>>

    /**
     * Alias reactive query returning all tasks sorted by due date ascending.
     */
    @Query("SELECT * FROM tasks ORDER BY due_date ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, due_date ASC")
    fun observeAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE is_completed = :isCompleted ORDER BY due_date ASC")
    fun observeTasksByCompletionStatus(isCompleted: Boolean): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE category = :category ORDER BY due_date ASC")
    fun observeTasksByCategory(category: ReminderCategory): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE priority = :priority ORDER BY due_date ASC")
    fun observeTasksByPriority(priority: ReminderPriority): Flow<List<Task>>

    @Query(
        "SELECT * FROM tasks WHERE is_completed = 0 AND due_date < :nowEpochMillis ORDER BY due_date ASC"
    )
    fun observeOverdueTasks(nowEpochMillis: Long): Flow<List<Task>>

    @Query(
        "SELECT * FROM tasks WHERE due_date BETWEEN :startEpochMillis AND :endEpochMillis ORDER BY due_date ASC"
    )
    fun observeTasksDueBetween(startEpochMillis: Long, endEpochMillis: Long): Flow<List<Task>>

    @Query(
        "SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY due_date ASC"
    )
    fun searchTasks(query: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY due_date ASC")
    suspend fun getAllTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE series_id = :seriesId ORDER BY due_date ASC")
    suspend fun getTasksBySeriesId(seriesId: String): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>): List<Long>

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks WHERE series_id = :seriesId")
    suspend fun deleteSeriesById(seriesId: String)

    @Query(
        "UPDATE tasks SET is_completed = :completed, completed_at = :completedAt, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun updateCompletionStatus(
        id: Long,
        completed: Boolean,
        completedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM tasks WHERE is_completed = 1")
    suspend fun deleteCompletedTasks()

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()
}

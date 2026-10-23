package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReminderEntity::class, Task::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class DayFlowDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: DayFlowDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_localDateStr` ON `reminders` (`localDateStr`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `series_id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `due_date` INTEGER NOT NULL,
                        `local_date_str` TEXT NOT NULL,
                        `local_time_str` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `is_completed` INTEGER NOT NULL,
                        `completed_at` INTEGER,
                        `recurrence_type` TEXT NOT NULL,
                        `recurrence_interval` INTEGER NOT NULL,
                        `recurrence_weekdays` TEXT NOT NULL,
                        `recurrence_end_date` TEXT,
                        `recurrence_max_occurrences` INTEGER,
                        `recurrence_rule` TEXT NOT NULL,
                        `is_recurrence_paused` INTEGER NOT NULL,
                        `notification_enabled` INTEGER NOT NULL,
                        `color_hex` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_due_date` ON `tasks` (`due_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_category` ON `tasks` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_priority` ON `tasks` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_is_completed` ON `tasks` (`is_completed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_series_id` ON `tasks` (`series_id`)")
            }
        }

        fun getInstance(context: Context): DayFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DayFlowDatabase::class.java,
                    "dayflow_reminders.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): DayFlowDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                DayFlowDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}

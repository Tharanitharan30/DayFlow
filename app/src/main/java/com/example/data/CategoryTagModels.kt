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

/**
 * Room Entity storing both preset category labels ('Work', 'Personal', 'Urgent', etc.)
 * and user-created custom category tags in SQLite.
 */
@Entity(
    tableName = "category_tags",
    indices = [
        Index(value = ["name"], name = "index_category_tags_name", unique = true)
    ]
)
data class CategoryTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "is_preset")
    val isPreset: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CategoryTagDao {
    @Query("SELECT * FROM category_tags ORDER BY is_preset DESC, created_at ASC")
    fun observeAllTags(): Flow<List<CategoryTagEntity>>

    @Query("SELECT * FROM category_tags ORDER BY is_preset DESC, created_at ASC")
    suspend fun getAllTagsSync(): List<CategoryTagEntity>

    @Query("SELECT * FROM category_tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTagByNameSync(name: String): CategoryTagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: CategoryTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<CategoryTagEntity>): List<Long>

    @Query("DELETE FROM category_tags WHERE LOWER(name) = LOWER(:name) AND is_preset = 0")
    suspend fun deleteCustomTagByName(name: String)

    @Query("DELETE FROM category_tags WHERE id = :id AND is_preset = 0")
    suspend fun deleteCustomTagById(id: Long)

    @Query("SELECT COUNT(*) FROM category_tags")
    suspend fun getTagCountSync(): Int
}

object DefaultCategoryTags {
    val SwatchPalette = listOf(
        "#0D9488", // Teal (Work)
        "#4F46E5", // Indigo (Personal)
        "#E11D48", // Crimson (Urgent)
        "#10B981", // Emerald (Health)
        "#D97706", // Amber (Finance)
        "#EC4899", // Pink (Errands)
        "#8B5CF6", // Violet (Habit)
        "#0284C7"  // Sky Blue (Custom)
    )

    fun presetEntities(now: Long = System.currentTimeMillis()): List<CategoryTagEntity> {
        return ReminderCategory.entries.mapIndexed { index, cat ->
            CategoryTagEntity(
                name = cat.shortLabel,
                colorHex = cat.defaultColorHex,
                isPreset = true,
                createdAt = now + index
            )
        }
    }
}

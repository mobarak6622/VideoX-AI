package com.example.data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectName: String,
    val sourceUriString: String,
    val exportedUriString: String?,
    val exportedDisplayPath: String?,
    val sourceResolution: String,
    val targetResolution: String,
    val sourceFps: Float,
    val targetFps: Int,
    val codecLabel: String,
    val upscaleModeLabel: String,
    val colorPresetLabel: String,
    val durationMs: Long,
    val outputFileSizeBytes: Long,
    val status: String, // "DRAFT", "COMPLETED", "FAILED", "CANCELLED"
    val lastCheckpointFrame: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY updatedAtEpochMs DESC")
    fun observeAllProjects(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): VideoProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: VideoProjectEntity): Long

    @Update
    suspend fun updateProject(project: VideoProjectEntity)

    @Query("UPDATE video_projects SET projectName = :newName, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun renameProject(id: Long, newName: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("DELETE FROM video_projects")
    suspend fun clearAllProjects()
}

@Database(entities = [VideoProjectEntity::class], version = 1, exportSchema = false)
abstract class VideoXDatabase : RoomDatabase() {
    abstract fun projectDao(): VideoProjectDao

    companion object {
        @Volatile
        private var INSTANCE: VideoXDatabase? = null

        fun getInstance(context: Context): VideoXDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoXDatabase::class.java,
                    "videox_ai_studio.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}

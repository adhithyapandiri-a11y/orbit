package com.orbit.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.orbit.core.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, due_date_epoch_day ASC, due_time_minute_of_day ASC, priority DESC, created_at_epoch_ms DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE due_date_epoch_day = :epochDay ORDER BY is_completed ASC, due_time_minute_of_day ASC, priority DESC")
    fun getTasksForDate(epochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskByIdDirect(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("UPDATE tasks SET is_completed = :isCompleted, completed_at_epoch_ms = :completedAt, updated_at_epoch_ms = :updatedAt WHERE id = :id")
    suspend fun updateCompletionStatus(id: String, isCompleted: Boolean, completedAt: Long?, updatedAt: Long)

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksDirect(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

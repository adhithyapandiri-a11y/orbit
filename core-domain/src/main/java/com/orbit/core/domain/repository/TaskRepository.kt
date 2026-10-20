package com.orbit.core.domain.repository

import com.orbit.core.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getTasks(): Flow<List<Task>>
    fun getTasksForDate(epochDay: Long): Flow<List<Task>>
    fun getTaskById(id: String): Flow<Task?>
    suspend fun upsertTask(task: Task)
    suspend fun deleteTask(id: String)
    suspend fun toggleTaskCompletion(id: String, completedAtEpochMs: Long?)
    suspend fun getAllTasksDirect(): List<Task>
    suspend fun deleteAllTasks()
}

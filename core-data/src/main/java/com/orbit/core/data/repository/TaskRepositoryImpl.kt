package com.orbit.core.data.repository

import com.orbit.core.data.db.dao.TaskDao
import com.orbit.core.data.db.entity.TaskEntity
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val taskDao: TaskDao
) : TaskRepository {

    override fun getTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { list -> list.map { it.toDomain() } }
    }

    override fun getTasksForDate(epochDay: Long): Flow<List<Task>> {
        return taskDao.getTasksForDate(epochDay).map { list -> list.map { it.toDomain() } }
    }

    override fun getTaskById(id: String): Flow<Task?> {
        return taskDao.getTaskById(id).map { it?.toDomain() }
    }

    override suspend fun upsertTask(task: Task) {
        taskDao.upsertTask(TaskEntity.fromDomain(task))
    }

    override suspend fun deleteTask(id: String) {
        taskDao.deleteTaskById(id)
    }

    override suspend fun toggleTaskCompletion(id: String, completedAtEpochMs: Long?) {
        val existing = taskDao.getTaskByIdDirect(id) ?: return
        val newStatus = !existing.isCompleted
        val completedAt = if (newStatus) completedAtEpochMs ?: System.currentTimeMillis() else null
        taskDao.updateCompletionStatus(
            id = id,
            isCompleted = newStatus,
            completedAt = completedAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun getAllTasksDirect(): List<Task> {
        return taskDao.getAllTasksDirect().map { it.toDomain() }
    }

    override suspend fun deleteAllTasks() {
        taskDao.deleteAll()
    }
}

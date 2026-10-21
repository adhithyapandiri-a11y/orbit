package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.OrbitErrorCode
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.TaskReminderScheduler
import com.orbit.core.domain.repository.TaskRepository

class UpdateTaskUseCase(
    private val taskRepository: TaskRepository,
    private val reminderScheduler: TaskReminderScheduler? = null
) {
    suspend operator fun invoke(task: Task): OrbitResult<Task> {
        val trimmedTitle = task.title.trim()
        if (trimmedTitle.isBlank()) {
            return OrbitResult.Error(
                code = OrbitErrorCode.VALIDATION_ERROR,
                message = "Task title cannot be blank"
            )
        }

        val now = System.currentTimeMillis()
        val updated = task.copy(
            title = trimmedTitle,
            notes = task.notes.trim(),
            updatedAtEpochMs = now
        )

        taskRepository.upsertTask(updated)

        if (updated.reminderAtEpochMs != null && updated.reminderAtEpochMs > now && !updated.isCompleted) {
            reminderScheduler?.scheduleReminder(updated)
        } else {
            reminderScheduler?.cancelReminder(updated.id)
        }

        return OrbitResult.Success(updated)
    }
}

package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.OrbitErrorCode
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.TaskReminderScheduler
import com.orbit.core.domain.repository.TaskRepository
import java.util.UUID

class CreateTaskUseCase(
    private val taskRepository: TaskRepository,
    private val reminderScheduler: TaskReminderScheduler? = null
) {
    suspend operator fun invoke(
        title: String,
        notes: String = "",
        dueDateEpochDay: Long? = null,
        dueTimeMinuteOfDay: Int? = null,
        priority: Priority = Priority.MEDIUM,
        reminderAtEpochMs: Long? = null
    ): OrbitResult<Task> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            return OrbitResult.Error(
                code = OrbitErrorCode.VALIDATION_ERROR,
                message = "Task title cannot be blank"
            )
        }

        val now = System.currentTimeMillis()
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = trimmedTitle,
            notes = notes.trim(),
            dueDateEpochDay = dueDateEpochDay,
            dueTimeMinuteOfDay = dueTimeMinuteOfDay,
            priority = priority,
            isCompleted = false,
            completedAtEpochMs = null,
            reminderAtEpochMs = reminderAtEpochMs,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        taskRepository.upsertTask(task)

        if (task.reminderAtEpochMs != null && task.reminderAtEpochMs > now) {
            reminderScheduler?.scheduleReminder(task)
        }

        return OrbitResult.Success(task)
    }
}

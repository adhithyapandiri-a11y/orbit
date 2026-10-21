package com.orbit.core.domain.usecase

import com.orbit.core.domain.repository.TaskReminderScheduler
import com.orbit.core.domain.repository.TaskRepository

class DeleteTaskUseCase(
    private val taskRepository: TaskRepository,
    private val reminderScheduler: TaskReminderScheduler? = null
) {
    suspend operator fun invoke(id: String) {
        taskRepository.deleteTask(id)
        reminderScheduler?.cancelReminder(id)
    }
}

package com.orbit.core.domain.usecase

import com.orbit.core.domain.repository.TaskReminderScheduler
import com.orbit.core.domain.repository.TaskRepository

class ToggleTaskCompletionUseCase(
    private val taskRepository: TaskRepository,
    private val reminderScheduler: TaskReminderScheduler? = null
) {
    suspend operator fun invoke(id: String) {
        val now = System.currentTimeMillis()
        taskRepository.toggleTaskCompletion(id, now)
        // If it was completed, cancel any pending reminder
        reminderScheduler?.cancelReminder(id)
    }
}

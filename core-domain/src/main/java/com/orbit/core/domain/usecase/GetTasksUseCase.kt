package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class GetTasksUseCase(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(
        filter: TaskFilter = TaskFilter.TODAY,
        currentEpochDay: Long = LocalDate.now().toEpochDay()
    ): Flow<List<Task>> {
        return taskRepository.getTasks().map { tasks ->
            when (filter) {
                TaskFilter.TODAY -> {
                    tasks.filter { task ->
                        // Show today's tasks, overdue uncompleted tasks, or active tasks without due date
                        if (task.isCompleted) {
                            task.dueDateEpochDay == currentEpochDay
                        } else {
                            task.dueDateEpochDay == null || task.dueDateEpochDay <= currentEpochDay
                        }
                    }
                }
                TaskFilter.UPCOMING -> {
                    tasks.filter { task ->
                        !task.isCompleted && task.dueDateEpochDay != null && task.dueDateEpochDay > currentEpochDay
                    }
                }
                TaskFilter.COMPLETED -> {
                    tasks.filter { it.isCompleted }
                }
                TaskFilter.ALL -> {
                    tasks
                }
            }
        }
    }
}

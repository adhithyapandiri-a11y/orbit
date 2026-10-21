package com.orbit.core.domain.repository

import com.orbit.core.domain.model.Task

interface TaskReminderScheduler {
    fun scheduleReminder(task: Task)
    fun cancelReminder(taskId: String)
}

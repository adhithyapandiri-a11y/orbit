package com.orbit.core.domain.model

import java.util.UUID

data class ExtractedTaskDraft(
    val title: String,
    val notes: String = "",
    val dueDateEpochDay: Long? = null,
    val dueTimeMinuteOfDay: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    val originalUserInput: String
) {
    fun toTask(): Task {
        val now = System.currentTimeMillis()
        return Task(
            id = UUID.randomUUID().toString(),
            title = title,
            notes = notes,
            dueDateEpochDay = dueDateEpochDay,
            dueTimeMinuteOfDay = dueTimeMinuteOfDay,
            priority = priority,
            isCompleted = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }
}

interface TaskExtractor {
    suspend fun extract(rawInput: String): ExtractedTaskDraft
}

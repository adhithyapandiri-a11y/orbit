package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val dueDateEpochDay: Long? = null,
    val dueTimeMinuteOfDay: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    val isCompleted: Boolean = false,
    val completedAtEpochMs: Long? = null,
    val reminderAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

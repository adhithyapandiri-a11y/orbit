package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["due_date_epoch_day"]),
        Index(value = ["is_completed"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val notes: String = "",
    @ColumnInfo(name = "due_date_epoch_day")
    val dueDateEpochDay: Long? = null,
    @ColumnInfo(name = "due_time_minute_of_day")
    val dueTimeMinuteOfDay: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long? = null,
    @ColumnInfo(name = "reminder_at_epoch_ms")
    val reminderAtEpochMs: Long? = null,
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
) {
    fun toDomain(): Task = Task(
        id = id,
        title = title,
        notes = notes,
        dueDateEpochDay = dueDateEpochDay,
        dueTimeMinuteOfDay = dueTimeMinuteOfDay,
        priority = priority,
        isCompleted = isCompleted,
        completedAtEpochMs = completedAtEpochMs,
        reminderAtEpochMs = reminderAtEpochMs,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            title = task.title,
            notes = task.notes,
            dueDateEpochDay = task.dueDateEpochDay,
            dueTimeMinuteOfDay = task.dueTimeMinuteOfDay,
            priority = task.priority,
            isCompleted = task.isCompleted,
            completedAtEpochMs = task.completedAtEpochMs,
            reminderAtEpochMs = task.reminderAtEpochMs,
            isArchived = task.isArchived,
            createdAtEpochMs = task.createdAtEpochMs,
            updatedAtEpochMs = task.updatedAtEpochMs
        )
    }
}

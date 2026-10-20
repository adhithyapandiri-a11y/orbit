package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.CalendarEventSnapshot

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["start_epoch_ms"]),
        Index(value = ["external_event_id"])
    ]
)
data class CalendarEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "calendar_id")
    val calendarId: Long,
    @ColumnInfo(name = "external_event_id")
    val externalEventId: Long,
    val title: String,
    val description: String = "",
    @ColumnInfo(name = "start_epoch_ms")
    val startEpochMs: Long,
    @ColumnInfo(name = "end_epoch_ms")
    val endEpochMs: Long,
    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean = false,
    val location: String = "",
    @ColumnInfo(name = "attendees_count")
    val attendeesCount: Int = 0,
    @ColumnInfo(name = "last_synced_at_epoch_ms")
    val lastSyncedAtEpochMs: Long
) {
    fun toDomain(): CalendarEventSnapshot = CalendarEventSnapshot(
        id = id,
        calendarId = calendarId,
        externalEventId = externalEventId,
        title = title,
        description = description,
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        isAllDay = isAllDay,
        location = location,
        attendeesCount = attendeesCount,
        lastSyncedAtEpochMs = lastSyncedAtEpochMs
    )

    companion object {
        fun fromDomain(event: CalendarEventSnapshot): CalendarEventEntity = CalendarEventEntity(
            id = event.id,
            calendarId = event.calendarId,
            externalEventId = event.externalEventId,
            title = event.title,
            description = event.description,
            startEpochMs = event.startEpochMs,
            endEpochMs = event.endEpochMs,
            isAllDay = event.isAllDay,
            location = event.location,
            attendeesCount = event.attendeesCount,
            lastSyncedAtEpochMs = event.lastSyncedAtEpochMs
        )
    }
}

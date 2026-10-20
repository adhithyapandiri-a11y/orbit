package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEventSnapshot(
    val id: String,
    val calendarId: Long,
    val externalEventId: Long,
    val title: String,
    val description: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val isAllDay: Boolean = false,
    val location: String = "",
    val attendeesCount: Int = 0,
    val lastSyncedAtEpochMs: Long
)

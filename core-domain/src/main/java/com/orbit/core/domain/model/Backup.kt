package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OrbitBackup(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val checksum: String = "",
    val tasks: List<Task> = emptyList(),
    val calendarEvents: List<CalendarEventSnapshot> = emptyList(),
    val meetingNotes: List<MeetingNote> = emptyList(),
    val videoItems: List<VideoItem> = emptyList(),
    val reelSessions: List<ReelCounterSession> = emptyList(),
    val counterEvents: List<CounterEvent> = emptyList(),
    val dailyReelStatistics: List<DailyReelStatistic> = emptyList()
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

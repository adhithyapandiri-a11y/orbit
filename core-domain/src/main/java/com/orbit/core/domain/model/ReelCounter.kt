package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CounterAction {
    INCREMENT,
    PAUSE,
    RESUME,
    RESET
}

@Serializable
enum class SessionStatus {
    ACTIVE,
    PAUSED,
    STOPPED
}

@Serializable
enum class OverlayDisplayMode {
    COMPACT,
    EXPANDED
}

@Serializable
enum class TrackerState {
    STOPPED,
    ACTIVE,
    PAUSED
}

@Serializable
data class ReelCounterSession(
    val id: String,
    val localDate: String, // ISO-8601 YYYY-MM-DD
    val startedAt: Long,
    val pausedAt: Long? = null,
    val stoppedAt: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CounterEvent(
    val id: String,
    val sessionId: String,
    val timestamp: Long,
    val action: CounterAction,
    val sequenceNumber: Long
)

@Serializable
data class DailyReelStatistic(
    val localDate: String, // ISO-8601 YYYY-MM-DD
    val manualCount: Int = 0,
    val activeDurationMillis: Long = 0L,
    val instagramForegroundDurationMillis: Long = 0L,
    val confidence: Float = 1.0f
)

@Serializable
data class OverlayPreference(
    val enabled: Boolean = false,
    val normalizedX: Float = 0.85f,
    val normalizedY: Float = 0.35f,
    val opacity: Float = 0.9f,
    val displayMode: OverlayDisplayMode = OverlayDisplayMode.COMPACT
)

@Serializable
data class TrackingCapabilityState(
    val hasUsageAccess: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val isForegroundServiceRunning: Boolean = false,
    val trackerState: TrackerState = TrackerState.STOPPED
)

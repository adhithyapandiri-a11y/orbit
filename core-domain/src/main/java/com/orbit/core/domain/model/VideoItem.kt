package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val id: String,
    val url: String,
    val title: String,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val estimatedDurationMinutes: Int? = null,
    val watchIntent: String = "",
    val isArchived: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

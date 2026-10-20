package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MeetingNote(
    val id: String,
    val eventSnapshotId: String,
    val title: String,
    val content: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

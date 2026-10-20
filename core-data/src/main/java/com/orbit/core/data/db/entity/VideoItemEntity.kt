package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.VideoItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "video_items",
    indices = [
        Index(value = ["is_archived"]),
        Index(value = ["url"])
    ]
)
data class VideoItemEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    @ColumnInfo(name = "estimated_duration_minutes")
    val estimatedDurationMinutes: Int? = null,
    @ColumnInfo(name = "watch_intent")
    val watchIntent: String = "",
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
) {
    fun toDomain(): VideoItem {
        val parsedTags: List<String> = try {
            Json.decodeFromString(tagsJson)
        } catch (_: Exception) {
            emptyList()
        }
        return VideoItem(
            id = id,
            url = url,
            title = title,
            tags = parsedTags,
            notes = notes,
            priority = priority,
            estimatedDurationMinutes = estimatedDurationMinutes,
            watchIntent = watchIntent,
            isArchived = isArchived,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    companion object {
        fun fromDomain(item: VideoItem): VideoItemEntity = VideoItemEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            tagsJson = Json.encodeToString(item.tags),
            notes = item.notes,
            priority = item.priority,
            estimatedDurationMinutes = item.estimatedDurationMinutes,
            watchIntent = item.watchIntent,
            isArchived = item.isArchived,
            createdAtEpochMs = item.createdAtEpochMs,
            updatedAtEpochMs = item.updatedAtEpochMs
        )
    }
}

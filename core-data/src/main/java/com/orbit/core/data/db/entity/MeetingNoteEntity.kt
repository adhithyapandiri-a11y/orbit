package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.MeetingNote

@Entity(
    tableName = "meeting_notes",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_snapshot_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_snapshot_id"])
    ]
)
data class MeetingNoteEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "event_snapshot_id")
    val eventSnapshotId: String,
    val title: String,
    val content: String = "",
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
) {
    fun toDomain(): MeetingNote = MeetingNote(
        id = id,
        eventSnapshotId = eventSnapshotId,
        title = title,
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    companion object {
        fun fromDomain(note: MeetingNote): MeetingNoteEntity = MeetingNoteEntity(
            id = note.id,
            eventSnapshotId = note.eventSnapshotId,
            title = note.title,
            content = note.content,
            createdAtEpochMs = note.createdAtEpochMs,
            updatedAtEpochMs = note.updatedAtEpochMs
        )
    }
}

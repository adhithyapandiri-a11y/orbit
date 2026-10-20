package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.CounterEvent

@Entity(
    tableName = "counter_events",
    foreignKeys = [
        ForeignKey(
            entity = ReelCounterSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "sequence_number"], unique = true)
    ]
)
data class CounterEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "timestamp_epoch_ms")
    val timestampEpochMs: Long,
    val action: CounterAction,
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Long
) {
    fun toDomain(): CounterEvent = CounterEvent(
        id = id,
        sessionId = sessionId,
        timestamp = timestampEpochMs,
        action = action,
        sequenceNumber = sequenceNumber
    )

    companion object {
        fun fromDomain(event: CounterEvent): CounterEventEntity = CounterEventEntity(
            id = event.id,
            sessionId = event.sessionId,
            timestampEpochMs = event.timestamp,
            action = event.action,
            sequenceNumber = event.sequenceNumber
        )
    }
}

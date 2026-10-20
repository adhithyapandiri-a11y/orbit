package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.ReelCounterSession
import com.orbit.core.domain.model.SessionStatus

@Entity(
    tableName = "reel_counter_sessions",
    indices = [
        Index(value = ["local_date"]),
        Index(value = ["status"])
    ]
)
data class ReelCounterSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "local_date")
    val localDate: String, // ISO-8601 YYYY-MM-DD
    @ColumnInfo(name = "started_at_epoch_ms")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "paused_at_epoch_ms")
    val pausedAtEpochMs: Long? = null,
    @ColumnInfo(name = "stopped_at_epoch_ms")
    val stoppedAtEpochMs: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
) {
    fun toDomain(): ReelCounterSession = ReelCounterSession(
        id = id,
        localDate = localDate,
        startedAt = startedAtEpochMs,
        pausedAt = pausedAtEpochMs,
        stoppedAt = stoppedAtEpochMs,
        status = status,
        createdAt = createdAtEpochMs,
        updatedAt = updatedAtEpochMs
    )

    companion object {
        fun fromDomain(session: ReelCounterSession): ReelCounterSessionEntity = ReelCounterSessionEntity(
            id = session.id,
            localDate = session.localDate,
            startedAtEpochMs = session.startedAt,
            pausedAtEpochMs = session.pausedAt,
            stoppedAtEpochMs = session.stoppedAt,
            status = session.status,
            createdAtEpochMs = session.createdAt,
            updatedAtEpochMs = session.updatedAt
        )
    }
}

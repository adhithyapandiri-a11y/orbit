package com.orbit.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.orbit.core.domain.model.DailyReelStatistic

@Entity(tableName = "daily_reel_statistics")
data class DailyReelStatisticEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_date")
    val localDate: String, // ISO-8601 YYYY-MM-DD
    @ColumnInfo(name = "manual_count")
    val manualCount: Int = 0,
    @ColumnInfo(name = "active_duration_ms")
    val activeDurationMs: Long = 0L,
    @ColumnInfo(name = "instagram_foreground_duration_ms")
    val instagramForegroundDurationMs: Long = 0L,
    val confidence: Float = 1.0f
) {
    fun toDomain(): DailyReelStatistic = DailyReelStatistic(
        localDate = localDate,
        manualCount = manualCount,
        activeDurationMillis = activeDurationMs,
        instagramForegroundDurationMillis = instagramForegroundDurationMs,
        confidence = confidence
    )

    companion object {
        fun fromDomain(stat: DailyReelStatistic): DailyReelStatisticEntity = DailyReelStatisticEntity(
            localDate = stat.localDate,
            manualCount = stat.manualCount,
            activeDurationMs = stat.activeDurationMillis,
            instagramForegroundDurationMs = stat.instagramForegroundDurationMillis,
            confidence = stat.confidence
        )
    }
}

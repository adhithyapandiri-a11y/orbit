package com.orbit.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.orbit.core.data.db.entity.CounterEventEntity
import com.orbit.core.data.db.entity.DailyReelStatisticEntity
import com.orbit.core.data.db.entity.ReelCounterSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelCounterDao {

    // --- Sessions ---
    @Query("SELECT * FROM reel_counter_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY started_at_epoch_ms DESC LIMIT 1")
    fun getActiveSession(): Flow<ReelCounterSessionEntity?>

    @Query("SELECT * FROM reel_counter_sessions WHERE id = :id")
    fun getSessionById(id: String): Flow<ReelCounterSessionEntity?>

    @Query("SELECT * FROM reel_counter_sessions WHERE id = :id")
    suspend fun getSessionByIdDirect(id: String): ReelCounterSessionEntity?

    @Query("SELECT * FROM reel_counter_sessions WHERE status != 'STOPPED'")
    suspend fun getUnfinishedSessionsDirect(): List<ReelCounterSessionEntity>

    @Query("SELECT * FROM reel_counter_sessions WHERE local_date = :localDate")
    suspend fun getSessionsForDateDirect(localDate: String): List<ReelCounterSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReelCounterSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ReelCounterSessionEntity>)

    @Update
    suspend fun updateSession(session: ReelCounterSessionEntity)

    @Query("SELECT * FROM reel_counter_sessions")
    suspend fun getAllSessionsDirect(): List<ReelCounterSessionEntity>

    @Query("DELETE FROM reel_counter_sessions")
    suspend fun deleteAllSessions()

    // --- Events (Append-only Log) ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: CounterEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CounterEventEntity>)

    @Query("SELECT * FROM counter_events WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    fun getEventsForSession(sessionId: String): Flow<List<CounterEventEntity>>

    @Query("SELECT * FROM counter_events WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    suspend fun getEventsForSessionDirect(sessionId: String): List<CounterEventEntity>

    @Query("SELECT COALESCE(MAX(sequence_number), 0) FROM counter_events WHERE session_id = :sessionId")
    suspend fun getMaxSequenceNumber(sessionId: String): Long

    @Query("SELECT * FROM counter_events")
    suspend fun getAllEventsDirect(): List<CounterEventEntity>

    @Query("DELETE FROM counter_events")
    suspend fun deleteAllEvents()

    // --- Daily Statistics ---
    @Query("SELECT * FROM daily_reel_statistics WHERE local_date = :localDate")
    fun getDailyStatistic(localDate: String): Flow<DailyReelStatisticEntity?>

    @Query("SELECT * FROM daily_reel_statistics WHERE local_date = :localDate")
    suspend fun getDailyStatisticDirect(localDate: String): DailyReelStatisticEntity?

    @Query("SELECT * FROM daily_reel_statistics ORDER BY local_date DESC LIMIT :limit")
    fun getRecentDailyStatistics(limit: Int): Flow<List<DailyReelStatisticEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStatistic(stat: DailyReelStatisticEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStatistics(stats: List<DailyReelStatisticEntity>)

    @Query("SELECT * FROM daily_reel_statistics")
    suspend fun getAllStatisticsDirect(): List<DailyReelStatisticEntity>

    @Query("DELETE FROM daily_reel_statistics")
    suspend fun deleteAllStatistics()
}

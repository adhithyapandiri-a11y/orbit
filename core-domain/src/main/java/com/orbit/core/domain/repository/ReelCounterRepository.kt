package com.orbit.core.domain.repository

import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.CounterEvent
import com.orbit.core.domain.model.DailyReelStatistic
import com.orbit.core.domain.model.ReelCounterSession
import kotlinx.coroutines.flow.Flow

interface ReelCounterRepository {
    fun getActiveSession(): Flow<ReelCounterSession?>
    fun getSessionById(sessionId: String): Flow<ReelCounterSession?>
    fun getEventsForSession(sessionId: String): Flow<List<CounterEvent>>
    fun getDailyStatistic(localDate: String): Flow<DailyReelStatistic?>
    fun getDailyStatistics(limit: Int): Flow<List<DailyReelStatistic>>

    suspend fun startSession(localDate: String, timestamp: Long): ReelCounterSession
    suspend fun recordEvent(sessionId: String, action: CounterAction, timestamp: Long): CounterEvent
    suspend fun pauseSession(sessionId: String, timestamp: Long)
    suspend fun resumeSession(sessionId: String, timestamp: Long)
    suspend fun stopSession(sessionId: String, timestamp: Long)
    suspend fun resetSession(sessionId: String, timestamp: Long)

    suspend fun reconcileUnfinishedSessions()
    suspend fun refreshDailyRollup(localDate: String)
    suspend fun updateInstagramForegroundDuration(localDate: String, foregroundDurationMs: Long)

    suspend fun getAllSessionsDirect(): List<ReelCounterSession>
    suspend fun getAllEventsDirect(): List<CounterEvent>
    suspend fun getAllStatisticsDirect(): List<DailyReelStatistic>
    suspend fun deleteAllCounterData()
}

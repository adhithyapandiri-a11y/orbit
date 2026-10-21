package com.orbit.core.data.repository

import androidx.room.withTransaction
import com.orbit.core.data.db.OrbitDatabase
import com.orbit.core.data.db.dao.ReelCounterDao
import com.orbit.core.data.db.entity.CounterEventEntity
import com.orbit.core.data.db.entity.DailyReelStatisticEntity
import com.orbit.core.data.db.entity.ReelCounterSessionEntity
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.CounterEvent
import com.orbit.core.domain.model.DailyReelStatistic
import com.orbit.core.domain.model.ReelCounterSession
import com.orbit.core.domain.model.SessionStatus
import com.orbit.core.domain.repository.ReelCounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ReelCounterRepositoryImpl(
    private val database: OrbitDatabase,
    private val reelCounterDao: ReelCounterDao
) : ReelCounterRepository {

    override fun getActiveSession(): Flow<ReelCounterSession?> {
        return reelCounterDao.getActiveSession().map { it?.toDomain() }
    }

    override fun getSessionById(sessionId: String): Flow<ReelCounterSession?> {
        return reelCounterDao.getSessionById(sessionId).map { it?.toDomain() }
    }

    override fun getEventsForSession(sessionId: String): Flow<List<CounterEvent>> {
        return reelCounterDao.getEventsForSession(sessionId).map { list -> list.map { it.toDomain() } }
    }

    override fun getDailyStatistic(localDate: String): Flow<DailyReelStatistic?> {
        return reelCounterDao.getDailyStatistic(localDate).map { it?.toDomain() }
    }

    override fun getDailyStatistics(limit: Int): Flow<List<DailyReelStatistic>> {
        return reelCounterDao.getRecentDailyStatistics(limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun startSession(localDate: String, timestamp: Long): ReelCounterSession {
        val sessionId = UUID.randomUUID().toString()
        val sessionEntity = ReelCounterSessionEntity(
            id = sessionId,
            localDate = localDate,
            startedAtEpochMs = timestamp,
            pausedAtEpochMs = null,
            stoppedAtEpochMs = null,
            status = SessionStatus.ACTIVE,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp
        )
        database.withTransaction {
            // Close any currently active sessions first to prevent orphaned active states
            val activeSessions = reelCounterDao.getUnfinishedSessionsDirect()
            for (unfinished in activeSessions) {
                reelCounterDao.updateSession(
                    unfinished.copy(
                        status = SessionStatus.STOPPED,
                        stoppedAtEpochMs = timestamp,
                        updatedAtEpochMs = timestamp
                    )
                )
            }
            reelCounterDao.insertSession(sessionEntity)
        }
        return sessionEntity.toDomain()
    }

    override suspend fun recordEvent(
        sessionId: String,
        action: CounterAction,
        timestamp: Long
    ): CounterEvent {
        return database.withTransaction {
            val session = reelCounterDao.getSessionByIdDirect(sessionId)
                ?: throw IllegalArgumentException("Session not found: $sessionId")

            if (session.status == SessionStatus.STOPPED) {
                throw IllegalStateException("Cannot record event on a stopped session: $sessionId")
            }

            val currentMaxSeq = reelCounterDao.getMaxSequenceNumber(sessionId)
            val newSeq = currentMaxSeq + 1L

            val event = CounterEventEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                timestampEpochMs = timestamp,
                action = action,
                sequenceNumber = newSeq
            )
            reelCounterDao.insertEvent(event)

            // Update session state based on event action
            when (action) {
                CounterAction.PAUSE -> {
                    reelCounterDao.updateSession(
                        session.copy(
                            status = SessionStatus.PAUSED,
                            pausedAtEpochMs = timestamp,
                            updatedAtEpochMs = timestamp
                        )
                    )
                }
                CounterAction.RESUME -> {
                    reelCounterDao.updateSession(
                        session.copy(
                            status = SessionStatus.ACTIVE,
                            pausedAtEpochMs = null,
                            updatedAtEpochMs = timestamp
                        )
                    )
                }
                CounterAction.RESET -> {
                    // Reset leaves session active, but resets sequence tracking
                    reelCounterDao.updateSession(session.copy(updatedAtEpochMs = timestamp))
                }
                CounterAction.INCREMENT -> {
                    reelCounterDao.updateSession(session.copy(updatedAtEpochMs = timestamp))
                }
            }

            // Derive daily totals transactionally
            computeAndPersistDailyRollup(session.localDate)

            event.toDomain()
        }
    }

    override suspend fun pauseSession(sessionId: String, timestamp: Long) {
        recordEvent(sessionId, CounterAction.PAUSE, timestamp)
    }

    override suspend fun resumeSession(sessionId: String, timestamp: Long) {
        recordEvent(sessionId, CounterAction.RESUME, timestamp)
    }

    override suspend fun stopSession(sessionId: String, timestamp: Long) {
        database.withTransaction {
            val session = reelCounterDao.getSessionByIdDirect(sessionId) ?: return@withTransaction
            reelCounterDao.updateSession(
                session.copy(
                    status = SessionStatus.STOPPED,
                    stoppedAtEpochMs = timestamp,
                    updatedAtEpochMs = timestamp
                )
            )
            computeAndPersistDailyRollup(session.localDate)
        }
    }

    override suspend fun resetSession(sessionId: String, timestamp: Long) {
        recordEvent(sessionId, CounterAction.RESET, timestamp)
    }

    override suspend fun reconcileUnfinishedSessions() {
        database.withTransaction {
            val unfinished = reelCounterDao.getUnfinishedSessionsDirect()
            val now = System.currentTimeMillis()
            val affectedDates = mutableSetOf<String>()
            for (session in unfinished) {
                val lastEvent = reelCounterDao.getEventsForSessionDirect(session.id).lastOrNull()
                val stopTimestamp = lastEvent?.timestampEpochMs ?: session.startedAtEpochMs
                reelCounterDao.updateSession(
                    session.copy(
                        status = SessionStatus.STOPPED,
                        stoppedAtEpochMs = stopTimestamp,
                        updatedAtEpochMs = now
                    )
                )
                affectedDates.add(session.localDate)
            }
            for (date in affectedDates) {
                computeAndPersistDailyRollup(date)
            }
        }
    }

    override suspend fun refreshDailyRollup(localDate: String) {
        database.withTransaction {
            computeAndPersistDailyRollup(localDate)
        }
    }

    override suspend fun updateInstagramForegroundDuration(localDate: String, foregroundDurationMs: Long) {
        database.withTransaction {
            val existing = reelCounterDao.getDailyStatisticDirect(localDate)
            val updated = (existing ?: DailyReelStatisticEntity(localDate = localDate)).copy(
                instagramForegroundDurationMs = foregroundDurationMs
            )
            reelCounterDao.upsertDailyStatistic(updated)
        }
    }

    /**
     * Source of truth computation:
     * Derives totals transactionally from the append-only event log.
     */
    private suspend fun computeAndPersistDailyRollup(localDate: String) {
        val sessions = reelCounterDao.getSessionsForDateDirect(localDate)
        var totalManualCount = 0
        var totalActiveDurationMs = 0L

        // Process sessions ordered by started_at_epoch_ms ASC
        for (session in sessions) {
            val events = reelCounterDao.getEventsForSessionDirect(session.id)
            var sessionActiveMs = 0L
            var intervalStart: Long? = session.startedAtEpochMs

            for (event in events) {
                when (event.action) {
                    CounterAction.INCREMENT -> {
                        totalManualCount++
                    }
                    CounterAction.RESET -> {
                        totalManualCount = 0
                    }
                    CounterAction.PAUSE -> {
                        if (intervalStart != null && event.timestampEpochMs >= intervalStart) {
                            sessionActiveMs += (event.timestampEpochMs - intervalStart)
                        }
                        intervalStart = null
                    }
                    CounterAction.RESUME -> {
                        if (intervalStart == null) {
                            intervalStart = event.timestampEpochMs
                        }
                    }
                }
            }

            // If session interval is still active, accumulate time up to stoppedAt or current time
            if (intervalStart != null) {
                val end = session.stoppedAtEpochMs
                    ?: if (session.status == SessionStatus.ACTIVE) {
                        maxOf(session.updatedAtEpochMs, System.currentTimeMillis())
                    } else {
                        session.updatedAtEpochMs
                    }
                if (end >= intervalStart) {
                    sessionActiveMs += (end - intervalStart)
                }
            }

            totalActiveDurationMs += sessionActiveMs
        }

        val existingStat = reelCounterDao.getDailyStatisticDirect(localDate)
        val fgDuration = existingStat?.instagramForegroundDurationMs ?: 0L

        val updatedStat = DailyReelStatisticEntity(
            localDate = localDate,
            manualCount = totalManualCount,
            activeDurationMs = totalActiveDurationMs,
            instagramForegroundDurationMs = fgDuration,
            confidence = 1.0f
        )
        reelCounterDao.upsertDailyStatistic(updatedStat)
    }

    override suspend fun getAllSessionsDirect(): List<ReelCounterSession> {
        return reelCounterDao.getAllSessionsDirect().map { it.toDomain() }
    }

    override suspend fun getAllEventsDirect(): List<CounterEvent> {
        return reelCounterDao.getAllEventsDirect().map { it.toDomain() }
    }

    override suspend fun getAllStatisticsDirect(): List<DailyReelStatistic> {
        return reelCounterDao.getAllStatisticsDirect().map { it.toDomain() }
    }

    override suspend fun deleteAllCounterData() {
        database.withTransaction {
            reelCounterDao.deleteAllEvents()
            reelCounterDao.deleteAllSessions()
            reelCounterDao.deleteAllStatistics()
        }
    }
}

package com.orbit.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.core.data.db.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar_events WHERE end_epoch_ms >= :startEpochMs AND start_epoch_ms <= :endEpochMs ORDER BY start_epoch_ms ASC")
    fun getEventsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    fun getEventById(id: String): Flow<CalendarEventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE end_epoch_ms < :beforeEpochMs")
    suspend fun deleteEventsBefore(beforeEpochMs: Long)

    @Query("SELECT * FROM calendar_events")
    suspend fun getAllEventsDirect(): List<CalendarEventEntity>

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}

package com.orbit.core.domain.repository

import com.orbit.core.domain.model.CalendarEventSnapshot
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {
    fun getEventsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEventSnapshot>>
    fun getEventById(id: String): Flow<CalendarEventSnapshot?>
    suspend fun upsertEvents(events: List<CalendarEventSnapshot>)
    suspend fun clearOldEvents(beforeEpochMs: Long)
    suspend fun getAllEventsDirect(): List<CalendarEventSnapshot>
    suspend fun deleteAllEvents()
}

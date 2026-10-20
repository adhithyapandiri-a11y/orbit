package com.orbit.core.data.repository

import com.orbit.core.data.db.dao.CalendarDao
import com.orbit.core.data.db.entity.CalendarEventEntity
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalendarRepositoryImpl(
    private val calendarDao: CalendarDao
) : CalendarRepository {

    override fun getEventsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEventSnapshot>> {
        return calendarDao.getEventsBetween(startEpochMs, endEpochMs).map { list -> list.map { it.toDomain() } }
    }

    override fun getEventById(id: String): Flow<CalendarEventSnapshot?> {
        return calendarDao.getEventById(id).map { it?.toDomain() }
    }

    override suspend fun upsertEvents(events: List<CalendarEventSnapshot>) {
        calendarDao.insertEvents(events.map { CalendarEventEntity.fromDomain(it) })
    }

    override suspend fun clearOldEvents(beforeEpochMs: Long) {
        calendarDao.deleteEventsBefore(beforeEpochMs)
    }

    override suspend fun getAllEventsDirect(): List<CalendarEventSnapshot> {
        return calendarDao.getAllEventsDirect().map { it.toDomain() }
    }

    override suspend fun deleteAllEvents() {
        calendarDao.deleteAll()
    }
}

package com.orbit.core.data.repository

import com.orbit.core.data.db.dao.MeetingNoteDao
import com.orbit.core.data.db.entity.MeetingNoteEntity
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.repository.MeetingNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeetingNoteRepositoryImpl(
    private val meetingNoteDao: MeetingNoteDao
) : MeetingNoteRepository {

    override fun getNoteForEvent(eventSnapshotId: String): Flow<MeetingNote?> {
        return meetingNoteDao.getNoteForEvent(eventSnapshotId).map { it?.toDomain() }
    }

    override fun getNoteById(id: String): Flow<MeetingNote?> {
        return meetingNoteDao.getNoteById(id).map { it?.toDomain() }
    }

    override fun getAllNotes(): Flow<List<MeetingNote>> {
        return meetingNoteDao.getAllNotes().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun upsertNote(note: MeetingNote) {
        meetingNoteDao.upsertNote(MeetingNoteEntity.fromDomain(note))
    }

    override suspend fun deleteNote(id: String) {
        meetingNoteDao.deleteNoteById(id)
    }

    override suspend fun getAllNotesDirect(): List<MeetingNote> {
        return meetingNoteDao.getAllNotesDirect().map { it.toDomain() }
    }

    override suspend fun deleteAllNotes() {
        meetingNoteDao.deleteAll()
    }
}

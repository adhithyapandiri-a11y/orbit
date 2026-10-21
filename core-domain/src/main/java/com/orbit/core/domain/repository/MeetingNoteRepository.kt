package com.orbit.core.domain.repository

import com.orbit.core.domain.model.MeetingNote
import kotlinx.coroutines.flow.Flow

interface MeetingNoteRepository {
    fun getNoteForEvent(eventSnapshotId: String): Flow<MeetingNote?>
    fun getNoteById(id: String): Flow<MeetingNote?>
    fun getAllNotes(): Flow<List<MeetingNote>>
    suspend fun upsertNote(note: MeetingNote)
    suspend fun deleteNote(id: String)
    suspend fun getAllNotesDirect(): List<MeetingNote>
    suspend fun deleteAllNotes()
}

package com.orbit.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.core.data.db.entity.MeetingNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingNoteDao {

    @Query("SELECT * FROM meeting_notes WHERE event_snapshot_id = :eventSnapshotId")
    fun getNoteForEvent(eventSnapshotId: String): Flow<MeetingNoteEntity?>

    @Query("SELECT * FROM meeting_notes WHERE id = :id")
    fun getNoteById(id: String): Flow<MeetingNoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: MeetingNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<MeetingNoteEntity>)

    @Query("DELETE FROM meeting_notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("SELECT * FROM meeting_notes")
    suspend fun getAllNotesDirect(): List<MeetingNoteEntity>

    @Query("DELETE FROM meeting_notes")
    suspend fun deleteAll()
}

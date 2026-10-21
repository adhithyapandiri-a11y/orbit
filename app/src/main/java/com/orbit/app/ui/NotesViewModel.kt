package com.orbit.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class NoteCategory {
    ALL,
    COLLEGE,
    PERSONAL,
    IDEAS,
    MEETINGS
}

data class NotesUiState(
    val notes: List<MeetingNote> = emptyList(),
    val filteredNotes: List<MeetingNote> = emptyList(),
    val activeCategory: NoteCategory = NoteCategory.ALL,
    val searchQuery: String = "",
    val selectedNote: MeetingNote? = null,
    val isEditSheetOpen: Boolean = false,
    val inputTitle: String = "",
    val inputContent: String = "",
    val inputCategory: String = "General"
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val meetingNoteRepository: MeetingNoteRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())

    val uiState: StateFlow<NotesUiState> = combine(
        meetingNoteRepository.getAllNotes(),
        _uiState
    ) { allNotes, state ->
        val query = state.searchQuery.trim().lowercase()

        val categoryFiltered = when (state.activeCategory) {
            NoteCategory.ALL -> allNotes
            NoteCategory.COLLEGE -> allNotes.filter {
                it.content.contains("[College]", ignoreCase = true) ||
                it.content.contains("#college", ignoreCase = true) ||
                it.content.contains("college", ignoreCase = true) ||
                it.title.contains("cs", ignoreCase = true) ||
                it.title.contains("ada", ignoreCase = true)
            }
            NoteCategory.PERSONAL -> allNotes.filter {
                it.content.contains("[Personal]", ignoreCase = true) ||
                it.content.contains("#personal", ignoreCase = true) ||
                it.content.contains("personal", ignoreCase = true) ||
                it.title.contains("plan", ignoreCase = true) ||
                it.title.contains("shopping", ignoreCase = true)
            }
            NoteCategory.IDEAS -> allNotes.filter {
                it.content.contains("[Ideas]", ignoreCase = true) ||
                it.content.contains("#idea", ignoreCase = true) ||
                it.content.contains("idea", ignoreCase = true) ||
                it.title.contains("project", ignoreCase = true)
            }
            NoteCategory.MEETINGS -> allNotes.filter {
                it.content.contains("[Meetings]", ignoreCase = true) ||
                (!it.eventSnapshotId.startsWith("event_note_") && !it.eventSnapshotId.startsWith("note_snapshot_") && it.eventSnapshotId.isNotBlank() && it.eventSnapshotId != "general_note")
            }
        }

        val searchFiltered = if (query.isNotBlank()) {
            categoryFiltered.filter {
                it.title.lowercase().contains(query) ||
                it.content.lowercase().contains(query)
            }
        } else {
            categoryFiltered
        }

        // Pinned notes appear at top, then ordered by most recently updated
        val sortedNotes = searchFiltered.sortedWith(
            compareByDescending<MeetingNote> { it.content.contains("[Pinned]") }
                .thenByDescending { it.updatedAtEpochMs }
        )

        state.copy(
            notes = allNotes,
            filteredNotes = sortedNotes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesUiState()
    )

    fun setCategory(category: NoteCategory) {
        _uiState.update { it.copy(activeCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openNewNoteSheet() {
        _uiState.update {
            it.copy(
                isEditSheetOpen = true,
                selectedNote = null,
                inputTitle = "",
                inputContent = "",
                inputCategory = "General"
            )
        }
    }

    fun openEditNoteSheet(note: MeetingNote) {
        val detectedCategory = when {
            note.content.contains("[College]", ignoreCase = true) -> "College"
            note.content.contains("[Personal]", ignoreCase = true) -> "Personal"
            note.content.contains("[Ideas]", ignoreCase = true) -> "Ideas"
            note.content.contains("[Meetings]", ignoreCase = true) -> "Meetings"
            else -> "General"
        }
        val cleanContent = note.content
            .replace("\\[(College|Personal|Ideas|Meetings|Pinned)\\]\\s*".toRegex(RegexOption.IGNORE_CASE), "")
            .trim()

        _uiState.update {
            it.copy(
                isEditSheetOpen = true,
                selectedNote = note,
                inputTitle = note.title,
                inputContent = cleanContent,
                inputCategory = detectedCategory
            )
        }
    }

    fun closeEditSheet() {
        _uiState.update { it.copy(isEditSheetOpen = false, selectedNote = null) }
    }

    fun updateInputTitle(title: String) {
        _uiState.update { it.copy(inputTitle = title) }
    }

    fun updateInputContent(content: String) {
        _uiState.update { it.copy(inputContent = content) }
    }

    fun updateInputCategory(category: String) {
        _uiState.update { it.copy(inputCategory = category) }
    }

    fun togglePinNote(note: MeetingNote) {
        viewModelScope.launch {
            val isCurrentlyPinned = note.content.contains("[Pinned]")
            val updatedContent = if (isCurrentlyPinned) {
                note.content.replace("[Pinned] ", "").replace("[Pinned]", "").trim()
            } else {
                "[Pinned] ${note.content.trim()}"
            }
            val updated = note.copy(
                content = updatedContent,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            meetingNoteRepository.upsertNote(updated)
        }
    }

    fun saveNote() {
        val state = _uiState.value
        val title = state.inputTitle.trim()
        if (title.isBlank()) return

        val now = System.currentTimeMillis()
        val noteId = state.selectedNote?.id ?: UUID.randomUUID().toString()
        val eventId = state.selectedNote?.eventSnapshotId ?: "event_note_$noteId"

        val isPinned = state.selectedNote?.content?.contains("[Pinned]") == true
        val categoryPrefix = if (state.inputCategory != "General") "[${state.inputCategory}] " else ""
        val pinPrefix = if (isPinned) "[Pinned] " else ""
        val finalContent = "$pinPrefix$categoryPrefix${state.inputContent.trim()}".trim()

        val note = MeetingNote(
            id = noteId,
            eventSnapshotId = eventId,
            title = title,
            content = finalContent,
            createdAtEpochMs = state.selectedNote?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now
        )

        viewModelScope.launch {
            // Ensure corresponding event snapshot exists to satisfy foreign key requirement safely
            calendarRepository.upsertEvents(
                listOf(
                    CalendarEventSnapshot(
                        id = eventId,
                        calendarId = 0,
                        externalEventId = 0,
                        title = title,
                        description = "Linked Orbit Note",
                        startEpochMs = now,
                        endEpochMs = now + 3600000,
                        lastSyncedAtEpochMs = now
                    )
                )
            )
            meetingNoteRepository.upsertNote(note)
            closeEditSheet()
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            meetingNoteRepository.deleteNote(noteId)
            closeEditSheet()
        }
    }
}

package com.orbit.app.ui

import com.google.common.truth.Truth.assertThat
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeNotes = MutableStateFlow<List<MeetingNote>>(emptyList())
    private val fakeNoteRepository = object : MeetingNoteRepository {
        val upsertedNotes = mutableListOf<MeetingNote>()
        val deletedNoteIds = mutableListOf<String>()

        override fun getNoteForEvent(eventSnapshotId: String): Flow<MeetingNote?> = flowOf(null)
        override fun getNoteById(id: String): Flow<MeetingNote?> = flowOf(null)
        override fun getAllNotes(): Flow<List<MeetingNote>> = fakeNotes.asStateFlow()
        override suspend fun upsertNote(note: MeetingNote) {
            upsertedNotes.add(note)
            fakeNotes.value = fakeNotes.value.filter { it.id != note.id } + note
        }
        override suspend fun deleteNote(id: String) {
            deletedNoteIds.add(id)
            fakeNotes.value = fakeNotes.value.filter { it.id != id }
        }
        override suspend fun getAllNotesDirect(): List<MeetingNote> = fakeNotes.value
        override suspend fun deleteAllNotes() {}
    }

    private val fakeCalendarRepository = object : CalendarRepository {
        override fun getEventsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEventSnapshot>> = flowOf(emptyList())
        override fun getEventById(id: String): Flow<CalendarEventSnapshot?> = flowOf(null)
        override suspend fun upsertEvents(events: List<CalendarEventSnapshot>) {}
        override suspend fun clearOldEvents(beforeEpochMs: Long) {}
        override suspend fun getAllEventsDirect(): List<CalendarEventSnapshot> = emptyList()
        override suspend fun deleteAllEvents() {}
    }

    private lateinit var viewModel: NotesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = NotesViewModel(
            meetingNoteRepository = fakeNoteRepository,
            calendarRepository = fakeCalendarRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun filtering_andSearching_worksCorrectly() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val note1 = MeetingNote("n1", "ev-1", "ADA Lecture", "CS college notes", 1000, 1000)
        val note2 = MeetingNote("n2", "ev-2", "Grocery List", "Personal shopping plan", 1000, 1000)
        val note3 = MeetingNote("n3", "ev-3", "Project Idea", "New startup idea", 1000, 1000)
        fakeNotes.value = listOf(note1, note2, note3)
        advanceUntilIdle()

        // ALL category (default)
        assertThat(viewModel.uiState.value.filteredNotes.map { it.id }).containsExactly("n1", "n2", "n3")

        // Search for "Lecture"
        viewModel.setSearchQuery("Lecture")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filteredNotes.map { it.id }).containsExactly("n1")

        // Reset query and filter by IDEAS
        viewModel.setSearchQuery("")
        viewModel.setCategory(NoteCategory.IDEAS)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filteredNotes.map { it.id }).containsExactly("n3")
    }

    @Test
    fun saveNote_persistsToRepository() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.openNewNoteSheet()
        viewModel.updateInputTitle("Meeting Summary")
        viewModel.updateInputContent("Discussed architecture roadmap")
        viewModel.saveNote()
        advanceUntilIdle()

        assertThat(fakeNoteRepository.upsertedNotes).hasSize(1)
        val saved = fakeNoteRepository.upsertedNotes.first()
        assertThat(saved.title).isEqualTo("Meeting Summary")
        assertThat(saved.content).isEqualTo("Discussed architecture roadmap")
        assertThat(viewModel.uiState.value.isEditSheetOpen).isFalse()
    }

    @Test
    fun deleteNote_callsRepository() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val note = MeetingNote("n-delete", "ev-99", "Trash", "To delete", 1000, 1000)
        fakeNotes.value = listOf(note)
        advanceUntilIdle()

        viewModel.deleteNote("n-delete")
        advanceUntilIdle()

        assertThat(fakeNoteRepository.deletedNoteIds).containsExactly("n-delete")
    }
}

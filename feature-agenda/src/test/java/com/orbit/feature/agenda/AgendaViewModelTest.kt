package com.orbit.feature.agenda

import com.google.common.truth.Truth.assertThat
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import com.orbit.core.domain.repository.TaskRepository
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeEvents = MutableStateFlow<List<CalendarEventSnapshot>>(emptyList())
    private val fakeCalendarRepository = object : CalendarRepository {
        override fun getEventsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEventSnapshot>> =
            fakeEvents.asStateFlow()
        override fun getEventById(id: String): Flow<CalendarEventSnapshot?> = flowOf(null)
        override suspend fun upsertEvents(events: List<CalendarEventSnapshot>) {}
        override suspend fun clearOldEvents(beforeEpochMs: Long) {}
        override suspend fun getAllEventsDirect(): List<CalendarEventSnapshot> = fakeEvents.value
        override suspend fun deleteAllEvents() {}
    }

    private val fakeTasks = MutableStateFlow<List<Task>>(emptyList())
    private val fakeTaskRepository = object : TaskRepository {
        val toggledIds = mutableListOf<String>()

        override fun getTasks(): Flow<List<Task>> = fakeTasks.asStateFlow()
        override fun getTasksForDate(epochDay: Long): Flow<List<Task>> = fakeTasks.asStateFlow()
        override fun getTaskById(id: String): Flow<Task?> = flowOf(null)
        override suspend fun upsertTask(task: Task) {}
        override suspend fun deleteTask(id: String) {}
        override suspend fun toggleTaskCompletion(id: String, completedAtEpochMs: Long?) {
            toggledIds.add(id)
        }
        override suspend fun getAllTasksDirect(): List<Task> = fakeTasks.value
        override suspend fun deleteAllTasks() {}
    }

    private val fakeNotes = MutableStateFlow<List<MeetingNote>>(emptyList())
    private val fakeMeetingNoteRepository = object : MeetingNoteRepository {
        val upsertedNotes = mutableListOf<MeetingNote>()

        override fun getAllNotes(): Flow<List<MeetingNote>> = fakeNotes
        override fun getNoteForEvent(eventSnapshotId: String): Flow<MeetingNote?> = flowOf(null)
        override fun getNoteById(id: String): Flow<MeetingNote?> = flowOf(null)
        override suspend fun upsertNote(note: MeetingNote) {
            upsertedNotes.add(note)
            fakeNotes.value = fakeNotes.value.filter { it.id != note.id } + note
        }
        override suspend fun deleteNote(id: String) {}
        override suspend fun getAllNotesDirect(): List<MeetingNote> = fakeNotes.value
        override suspend fun deleteAllNotes() {}
    }

    private lateinit var viewModel: AgendaViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AgendaViewModel(
            calendarRepository = fakeCalendarRepository,
            taskRepository = fakeTaskRepository,
            meetingNoteRepository = fakeMeetingNoteRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun timelineItems_whenPermissionGranted_showsBothEventsAndTasks() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val eventStart = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val eventEnd = today.atTime(11, 0).atZone(zone).toInstant().toEpochMilli()

        val event = CalendarEventSnapshot(
            id = "ev-1",
            calendarId = 1,
            externalEventId = 100,
            title = "OS Lecture",
            description = "Operating Systems",
            startEpochMs = eventStart,
            endEpochMs = eventEnd,
            isAllDay = false,
            location = "Hall A",
            attendeesCount = 30,
            lastSyncedAtEpochMs = 1000
        )
        fakeEvents.value = listOf(event)

        val task = Task(
            id = "t-1",
            title = "Assignment",
            dueDateEpochDay = today.toEpochDay(),
            dueTimeMinuteOfDay = 12 * 60,
            priority = Priority.HIGH,
            isCompleted = false,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )
        fakeTasks.value = listOf(task)

        viewModel.setCalendarPermission(true)
        advanceUntilIdle()

        val items = viewModel.uiState.value.timelineItems
        assertThat(items).hasSize(2)
        assertThat(items[0].title).isEqualTo("OS Lecture")
        assertThat(items[0].isLocalTask).isFalse()
        assertThat(items[1].title).isEqualTo("Assignment")
        assertThat(items[1].isLocalTask).isTrue()
    }

    @Test
    fun timelineItems_whenPermissionDenied_showsOnlyLocalTasks() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val today = LocalDate.now()
        val event = CalendarEventSnapshot(
            id = "ev-2",
            calendarId = 1,
            externalEventId = 101,
            title = "External Meeting",
            startEpochMs = System.currentTimeMillis(),
            endEpochMs = System.currentTimeMillis() + 3600000,
            lastSyncedAtEpochMs = 1000
        )
        fakeEvents.value = listOf(event)

        val task = Task(
            id = "t-2",
            title = "Local Task",
            dueDateEpochDay = today.toEpochDay(),
            dueTimeMinuteOfDay = 9 * 60,
            isCompleted = false,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )
        fakeTasks.value = listOf(task)

        viewModel.setCalendarPermission(false)
        advanceUntilIdle()

        val items = viewModel.uiState.value.timelineItems
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Local Task")
        assertThat(items[0].isLocalTask).isTrue()
    }

    @Test
    fun dateNavigation_updatesSelectedDate() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val today = LocalDate.now()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today)

        viewModel.nextDay()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today.plusDays(1))

        viewModel.previousDay()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today)
    }

    @Test
    fun meetingNotes_openAndSave_persistsNote() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val event = CalendarEventSnapshot(
            id = "ev-3",
            calendarId = 1,
            externalEventId = 102,
            title = "Client Sync",
            startEpochMs = 1000,
            endEpochMs = 2000,
            lastSyncedAtEpochMs = 1000
        )

        viewModel.openMeetingNote(event)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isMeetingNoteSheetOpen).isTrue()
        assertThat(viewModel.uiState.value.meetingNoteTitle).isEqualTo("Client Sync")

        viewModel.updateMeetingNoteContent("Discussion on roadmap Q4")
        viewModel.saveMeetingNote()
        advanceUntilIdle()

        assertThat(fakeMeetingNoteRepository.upsertedNotes).hasSize(1)
        val saved = fakeMeetingNoteRepository.upsertedNotes.first()
        assertThat(saved.title).isEqualTo("Client Sync")
        assertThat(saved.content).isEqualTo("Discussion on roadmap Q4")
        assertThat(viewModel.uiState.value.isMeetingNoteSheetOpen).isFalse()
    }

    @Test
    fun toggleTaskCompletion_callsRepository() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val task = Task(
            id = "t-99",
            title = "Task from Timeline",
            isCompleted = false,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )

        viewModel.toggleTaskCompletion(task)
        advanceUntilIdle()

        assertThat(fakeTaskRepository.toggledIds).containsExactly("t-99")
    }
}

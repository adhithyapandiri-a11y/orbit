package com.orbit.app.ui

import com.google.common.truth.Truth.assertThat
import com.orbit.core.ai.provider.OfflineDeterministicAiProvider
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.repository.VideoInboxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeTaskRepository = object : TaskRepository {
        val upsertedTasks = mutableListOf<Task>()
        val directTasks = mutableListOf<Task>()

        override fun getTasks(): Flow<List<Task>> = flowOf(directTasks)
        override fun getTasksForDate(epochDay: Long): Flow<List<Task>> = flowOf(directTasks.filter { it.dueDateEpochDay == epochDay })
        override fun getTaskById(id: String): Flow<Task?> = flowOf(null)
        override suspend fun upsertTask(task: Task) {
            upsertedTasks.add(task)
            directTasks.add(task)
        }
        override suspend fun deleteTask(id: String) {}
        override suspend fun toggleTaskCompletion(id: String, completedAtEpochMs: Long?) {}
        override suspend fun getAllTasksDirect(): List<Task> = directTasks
        override suspend fun deleteAllTasks() {}
    }

    private val fakeMeetingNoteRepository = object : MeetingNoteRepository {
        val upsertedNotes = mutableListOf<MeetingNote>()
        val directNotes = mutableListOf<MeetingNote>()

        override fun getNoteForEvent(eventSnapshotId: String): Flow<MeetingNote?> = flowOf(null)
        override fun getNoteById(id: String): Flow<MeetingNote?> = flowOf(null)
        override fun getAllNotes(): Flow<List<MeetingNote>> = flowOf(directNotes)
        override suspend fun upsertNote(note: MeetingNote) {
            upsertedNotes.add(note)
            directNotes.add(note)
        }
        override suspend fun deleteNote(id: String) {}
        override suspend fun getAllNotesDirect(): List<MeetingNote> = directNotes
        override suspend fun deleteAllNotes() {}
    }

    private val fakeCalendarRepository = object : CalendarRepository {
        val directEvents = mutableListOf<CalendarEventSnapshot>()

        override fun getEventsBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<CalendarEventSnapshot>> =
            flowOf(directEvents.filter { it.startEpochMs in startEpochMs until endEpochMs })
        override fun getEventById(id: String): Flow<CalendarEventSnapshot?> = flowOf(null)
        override suspend fun upsertEvents(events: List<CalendarEventSnapshot>) {
            directEvents.addAll(events)
        }
        override suspend fun clearOldEvents(beforeEpochMs: Long) {}
        override suspend fun getAllEventsDirect(): List<CalendarEventSnapshot> = directEvents
        override suspend fun deleteAllEvents() {}
    }

    private val fakeVideoInboxRepository = object : VideoInboxRepository {
        val directVideos = mutableListOf<VideoItem>()

        override fun getVideos(includeArchived: Boolean): Flow<List<VideoItem>> = flowOf(directVideos)
        override fun getVideoById(id: String): Flow<VideoItem?> = flowOf(null)
        override suspend fun upsertVideo(video: VideoItem) {
            directVideos.add(video)
        }
        override suspend fun archiveVideo(id: String, isArchived: Boolean) {}
        override suspend fun deleteVideo(id: String) {}
        override suspend fun getAllVideosDirect(): List<VideoItem> = directVideos
        override suspend fun deleteAllVideos() {}
    }

    private val fixedToday = LocalDate.of(2026, 10, 21)
    private lateinit var viewModel: AiAssistantViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val aiProvider = OfflineDeterministicAiProvider(dateSupplier = { fixedToday })
        viewModel = AiAssistantViewModel(
            taskRepository = fakeTaskRepository,
            meetingNoteRepository = fakeMeetingNoteRepository,
            calendarRepository = fakeCalendarRepository,
            videoInboxRepository = fakeVideoInboxRepository,
            aiProvider = aiProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessage_withTaskKeyword_proposesTaskAction() = runTest {
        viewModel.sendMessage("Add a task to study algorithms !high")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        assertThat(messages).hasSize(3) // initial greeting + user msg + assistant msg

        val lastMessage = messages.last()
        assertThat(lastMessage.isFromUser).isFalse()
        assertThat(lastMessage.proposedAction).isInstanceOf(ProposedAction.CreateTask::class.java)

        // Execute action
        viewModel.executeAction(lastMessage.id, lastMessage.proposedAction!!)
        advanceUntilIdle()

        assertThat(fakeTaskRepository.upsertedTasks).hasSize(1)
        val created = fakeTaskRepository.upsertedTasks.first()
        assertThat(created.title).contains("study algorithms")
        assertThat(created.priority).isEqualTo(Priority.HIGH)
        assertThat(viewModel.uiState.value.messages.last().actionExecuted).isTrue()
    }

    @Test
    fun sendMessage_withNoteKeyword_proposesNoteAction() = runTest {
        viewModel.sendMessage("Save note: system architecture draft")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        val lastMessage = messages.last()
        assertThat(lastMessage.proposedAction).isInstanceOf(ProposedAction.CreateNote::class.java)

        // Execute action
        viewModel.executeAction(lastMessage.id, lastMessage.proposedAction!!)
        advanceUntilIdle()

        assertThat(fakeMeetingNoteRepository.upsertedNotes).hasSize(1)
        assertThat(viewModel.uiState.value.messages.last().actionExecuted).isTrue()
    }

    @Test
    fun sendMessage_searchNotes_returnsMatchingNotesDirectly() = runTest {
        fakeMeetingNoteRepository.upsertNote(
            MeetingNote(
                id = "n1",
                eventSnapshotId = "e1",
                title = "Compose Navigation Notes",
                content = "Discussing type safety in Navigation Compose 2.8",
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L
            )
        )

        viewModel.sendMessage("Search notes for Navigation")
        advanceUntilIdle()

        val lastMessage = viewModel.uiState.value.messages.last()
        assertThat(lastMessage.isFromUser).isFalse()
        assertThat(lastMessage.text).contains("Compose Navigation Notes")
        assertThat(lastMessage.proposedAction).isNull()
    }

    @Test
    fun sendMessage_searchVideos_returnsMatchingVideosDirectly() = runTest {
        fakeVideoInboxRepository.upsertVideo(
            VideoItem(
                id = "v1",
                url = "https://youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Android Jetpack Compose Deep Dive",
                tags = listOf("Android", "Compose"),
                notes = "Great architecture overview",
                priority = Priority.HIGH,
                estimatedDurationMinutes = 25,
                watchIntent = "Learn Compose",
                isArchived = false,
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L
            )
        )

        viewModel.sendMessage("Find video about Jetpack")
        advanceUntilIdle()

        val lastMessage = viewModel.uiState.value.messages.last()
        assertThat(lastMessage.isFromUser).isFalse()
        assertThat(lastMessage.text).contains("Android Jetpack Compose Deep Dive")
        assertThat(lastMessage.proposedAction).isNull()
    }

    @Test
    fun sendMessage_queryCalendar_returnsSchedule() = runTest {
        val startMs = fixedToday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 36000000L
        fakeCalendarRepository.upsertEvents(
            listOf(
                CalendarEventSnapshot(
                    id = "c1",
                    calendarId = 1,
                    externalEventId = 101,
                    title = "Orbit Sprint Retrospective",
                    description = "Sprint 4 Review",
                    startEpochMs = startMs,
                    endEpochMs = startMs + 3600000L,
                    lastSyncedAtEpochMs = startMs
                )
            )
        )

        viewModel.sendMessage("What is on my calendar today?")
        advanceUntilIdle()

        val lastMessage = viewModel.uiState.value.messages.last()
        assertThat(lastMessage.isFromUser).isFalse()
        assertThat(lastMessage.text).contains("Orbit Sprint Retrospective")
    }

    @Test
    fun sendMessage_summarize_returnsProductivitySummary() = runTest {
        fakeTaskRepository.upsertTask(
            Task(
                id = "t1",
                title = "Finish tests",
                notes = "",
                dueDateEpochDay = fixedToday.toEpochDay(),
                dueTimeMinuteOfDay = null,
                priority = Priority.HIGH,
                isCompleted = true,
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L
            )
        )

        viewModel.sendMessage("Summarize my day")
        advanceUntilIdle()

        val lastMessage = viewModel.uiState.value.messages.last()
        assertThat(lastMessage.isFromUser).isFalse()
        assertThat(lastMessage.text).contains("productivity summary")
        assertThat(lastMessage.text).contains("1 completed")
    }
}

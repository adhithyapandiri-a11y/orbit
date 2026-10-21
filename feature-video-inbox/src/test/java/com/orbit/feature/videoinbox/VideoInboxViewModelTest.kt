package com.orbit.feature.videoinbox

import com.google.common.truth.Truth.assertThat
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.repository.VideoInboxRepository
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
class VideoInboxViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val fakeVideoRepository = object : VideoInboxRepository {
        val upsertedVideos = mutableListOf<VideoItem>()
        val archivedIds = mutableListOf<Pair<String, Boolean>>()
        val deletedIds = mutableListOf<String>()

        override fun getVideos(includeArchived: Boolean): Flow<List<VideoItem>> = fakeVideos.asStateFlow()
        override fun getVideoById(id: String): Flow<VideoItem?> = flowOf(null)
        override suspend fun upsertVideo(video: VideoItem) {
            upsertedVideos.add(video)
            fakeVideos.value = fakeVideos.value.filter { it.id != video.id } + video
        }
        override suspend fun archiveVideo(id: String, isArchived: Boolean) {
            archivedIds.add(id to isArchived)
            fakeVideos.value = fakeVideos.value.map {
                if (it.id == id) it.copy(isArchived = isArchived) else it
            }
        }
        override suspend fun deleteVideo(id: String) {
            deletedIds.add(id)
            fakeVideos.value = fakeVideos.value.filter { it.id != id }
        }
        override suspend fun getAllVideosDirect(): List<VideoItem> = fakeVideos.value
        override suspend fun deleteAllVideos() {}
    }

    private val fakeTaskRepository = object : TaskRepository {
        val upsertedTasks = mutableListOf<Task>()

        override fun getTasks(): Flow<List<Task>> = flowOf(emptyList())
        override fun getTasksForDate(epochDay: Long): Flow<List<Task>> = flowOf(emptyList())
        override fun getTaskById(id: String): Flow<Task?> = flowOf(null)
        override suspend fun upsertTask(task: Task) {
            upsertedTasks.add(task)
        }
        override suspend fun deleteTask(id: String) {}
        override suspend fun toggleTaskCompletion(id: String, completedAtEpochMs: Long?) {}
        override suspend fun getAllTasksDirect(): List<Task> = emptyList()
        override suspend fun deleteAllTasks() {}
    }

    private lateinit var viewModel: VideoInboxViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoInboxViewModel(
            videoInboxRepository = fakeVideoRepository,
            taskRepository = fakeTaskRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun filtering_byTabsAndSearchQuery_worksAccurately() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val vid1 = VideoItem(
            id = "v1",
            url = "https://youtube.com/watch?v=1",
            title = "Dynamic Programming",
            watchIntent = "SAVED",
            isArchived = false,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )
        val vid2 = VideoItem(
            id = "v2",
            url = "https://youtube.com/watch?v=2",
            title = "System Design",
            watchIntent = "WATCH_LATER",
            isArchived = false,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )
        val vid3 = VideoItem(
            id = "v3",
            url = "https://youtube.com/watch?v=3",
            title = "Archived Lecture",
            watchIntent = "SAVED",
            isArchived = true,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )
        fakeVideos.value = listOf(vid1, vid2, vid3)
        advanceUntilIdle()

        // Tab SAVED (default)
        assertThat(viewModel.uiState.value.filteredVideos.map { it.id }).containsExactly("v1")

        // Switch to WATCH_LATER
        viewModel.setTab(VideoInboxTab.WATCH_LATER)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filteredVideos.map { it.id }).containsExactly("v2")

        // Switch to ARCHIVED
        viewModel.setTab(VideoInboxTab.ARCHIVED)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filteredVideos.map { it.id }).containsExactly("v3")

        // Switch back to SAVED and search for "Programming"
        viewModel.setTab(VideoInboxTab.SAVED)
        viewModel.setSearchQuery("Programming")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filteredVideos.map { it.id }).containsExactly("v1")

        // Search with non-matching query
        viewModel.setSearchQuery("Quantum")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.filteredVideos).isEmpty()
    }

    @Test
    fun convertVideoToTask_createsTaskInRepository() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val video = VideoItem(
            id = "v10",
            url = "https://youtube.com/watch?v=xyz",
            title = "Neural Networks",
            priority = Priority.HIGH,
            isArchived = false,
            createdAtEpochMs = 1000,
            updatedAtEpochMs = 1000
        )

        viewModel.convertVideoToTask(video)
        advanceUntilIdle()

        assertThat(fakeTaskRepository.upsertedTasks).hasSize(1)
        val task = fakeTaskRepository.upsertedTasks.first()
        assertThat(task.title).isEqualTo("Watch: Neural Networks")
        assertThat(task.priority).isEqualTo(Priority.HIGH)
        assertThat(viewModel.uiState.value.statusMessage).contains("Added to today's tasks")
    }

    @Test
    fun saveVideo_parsesUrlAndPersists() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.openAddVideoSheet()
        viewModel.updateInputUrl("https://youtu.be/abc12345678")
        viewModel.updateInputTitle("Custom Title")
        viewModel.updateInputNotes("Study guide")
        viewModel.updateInputTags("kotlin, android")
        viewModel.saveVideo()
        advanceUntilIdle()

        assertThat(fakeVideoRepository.upsertedVideos).hasSize(1)
        val saved = fakeVideoRepository.upsertedVideos.first()
        assertThat(saved.title).isEqualTo("Custom Title")
        assertThat(saved.tags).containsExactly("kotlin", "android")
        assertThat(viewModel.uiState.value.isAddVideoSheetOpen).isFalse()
    }
}

package com.orbit.feature.today

import com.orbit.core.ai.OfflineDeterministicTaskExtractor
import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.model.TaskExtractor
import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.repository.PreferencesRepository
import com.orbit.core.domain.repository.TaskReminderScheduler
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.usecase.CreateTaskUseCase
import com.orbit.core.domain.usecase.DeleteTaskUseCase
import com.orbit.core.domain.usecase.ExtractTaskWithReviewUseCase
import com.orbit.core.domain.usecase.GetPreferencesUseCase
import com.orbit.core.domain.usecase.GetTasksUseCase
import com.orbit.core.domain.usecase.TaskFilter
import com.orbit.core.domain.usecase.ToggleTaskCompletionUseCase
import com.orbit.core.domain.usecase.UpdateTaskUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeTaskRepository: FakeTaskRepository
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var fakeReminderScheduler: FakeReminderScheduler
    private lateinit var taskExtractor: TaskExtractor
    private lateinit var viewModel: TodayViewModel

    private val referenceDate = LocalDate.of(2026, 10, 21)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeTaskRepository = FakeTaskRepository()
        fakePreferencesRepository = FakePreferencesRepository()
        fakeReminderScheduler = FakeReminderScheduler()
        taskExtractor = OfflineDeterministicTaskExtractor { referenceDate }

        val getTasksUseCase = GetTasksUseCase(fakeTaskRepository)
        val createTaskUseCase = CreateTaskUseCase(fakeTaskRepository, fakeReminderScheduler)
        val updateTaskUseCase = UpdateTaskUseCase(fakeTaskRepository, fakeReminderScheduler)
        val toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(fakeTaskRepository, fakeReminderScheduler)
        val deleteTaskUseCase = DeleteTaskUseCase(fakeTaskRepository, fakeReminderScheduler)
        val extractTaskWithReviewUseCase = ExtractTaskWithReviewUseCase(taskExtractor)
        val getPreferencesUseCase = GetPreferencesUseCase(fakePreferencesRepository)

        viewModel = TodayViewModel(
            getTasksUseCase = getTasksUseCase,
            createTaskUseCase = createTaskUseCase,
            updateTaskUseCase = updateTaskUseCase,
            toggleTaskCompletionUseCase = toggleTaskCompletionUseCase,
            deleteTaskUseCase = deleteTaskUseCase,
            extractTaskWithReviewUseCase = extractTaskWithReviewUseCase,
            getPreferencesUseCase = getPreferencesUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun quickCaptureSubmit_parsesDateAndTimeDeterministically() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateQuickCaptureText("Dentist appointment tomorrow at 5pm")
        viewModel.quickCaptureSubmit()
        advanceUntilIdle()

        val tasks = fakeTaskRepository.getAllTasksDirect()
        assertThat(tasks).hasSize(1)
        val task = tasks.first()
        assertThat(task.title).isEqualTo("Dentist appointment")
        assertThat(task.dueTimeMinuteOfDay).isEqualTo(17 * 60)
        assertThat(viewModel.uiState.value.quickCaptureText).isEmpty()
    }

    @Test
    fun startAiReviewExtraction_alwaysShowsReviewScreenBeforeWritingData() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateQuickCaptureText("Prepare quarterly report in 3 days !urgent")
        viewModel.startAiReviewExtraction()
        advanceUntilIdle()

        // Verify that NO task was written to the repository yet
        assertThat(fakeTaskRepository.getAllTasksDirect()).isEmpty()

        // Verify review screen draft is shown with preserved original text
        val draft = viewModel.uiState.value.reviewDraft
        assertThat(draft).isNotNull()
        assertThat(draft?.originalUserInput).isEqualTo("Prepare quarterly report in 3 days !urgent")
        assertThat(draft?.priority).isEqualTo(Priority.URGENT)

        // Confirm review draft
        viewModel.confirmReviewDraft(
            title = draft!!.title,
            notes = "Reviewed and approved",
            dueDateEpochDay = draft.dueDateEpochDay,
            dueTimeMinuteOfDay = draft.dueTimeMinuteOfDay,
            priority = draft.priority,
            reminderAtEpochMs = null
        )
        advanceUntilIdle()

        // Now it is persisted
        val saved = fakeTaskRepository.getAllTasksDirect()
        assertThat(saved).hasSize(1)
        assertThat(saved.first().title).isEqualTo("Prepare quarterly report")
        assertThat(saved.first().notes).isEqualTo("Reviewed and approved")
        assertThat(viewModel.uiState.value.reviewDraft).isNull()
    }

    @Test
    fun aiExtraction_whenFails_preservesOriginalUserInput() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val failingExtractor = object : TaskExtractor {
            override suspend fun extract(rawInput: String): ExtractedTaskDraft {
                throw RuntimeException("AI inference unavailable or malformed")
            }
        }
        val failingUseCase = ExtractTaskWithReviewUseCase(failingExtractor)

        val failingVm = TodayViewModel(
            getTasksUseCase = GetTasksUseCase(fakeTaskRepository),
            createTaskUseCase = CreateTaskUseCase(fakeTaskRepository, fakeReminderScheduler),
            updateTaskUseCase = UpdateTaskUseCase(fakeTaskRepository, fakeReminderScheduler),
            toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(fakeTaskRepository, fakeReminderScheduler),
            deleteTaskUseCase = DeleteTaskUseCase(fakeTaskRepository, fakeReminderScheduler),
            extractTaskWithReviewUseCase = failingUseCase,
            getPreferencesUseCase = GetPreferencesUseCase(fakePreferencesRepository)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            failingVm.uiState.collect {}
        }

        failingVm.updateQuickCaptureText("Raw unparsed user thoughts about life")
        failingVm.startAiReviewExtraction()
        advanceUntilIdle()

        val draft = failingVm.uiState.value.reviewDraft
        assertThat(draft).isNotNull()
        assertThat(draft?.title).isEqualTo("Raw unparsed user thoughts about life")
        assertThat(draft?.originalUserInput).isEqualTo("Raw unparsed user thoughts about life")
    }

    @Test
    fun toggleTaskCompletion_togglesStateAndCancelsReminder() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateQuickCaptureText("Simple task")
        viewModel.quickCaptureSubmit()
        advanceUntilIdle()

        val task = fakeTaskRepository.getAllTasksDirect().first()
        assertThat(task.isCompleted).isFalse()

        viewModel.toggleTaskCompletion(task.id)
        advanceUntilIdle()

        val updated = fakeTaskRepository.getAllTasksDirect().first()
        assertThat(updated.isCompleted).isTrue()
        assertThat(fakeReminderScheduler.cancelledTasks).contains(task.id)
    }

    @Test
    fun deleteTask_removesTaskAndCancelsReminder() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateQuickCaptureText("Task to delete")
        viewModel.quickCaptureSubmit()
        advanceUntilIdle()

        val task = fakeTaskRepository.getAllTasksDirect().first()
        viewModel.deleteTask(task.id)
        advanceUntilIdle()

        assertThat(fakeTaskRepository.getAllTasksDirect()).isEmpty()
        assertThat(fakeReminderScheduler.cancelledTasks).contains(task.id)
    }

    @Test
    fun filterTasks_today_upcoming_completed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val todayEpoch = LocalDate.now().toEpochDay()
        val todayTask = Task(
            id = "t1",
            title = "Today task",
            dueDateEpochDay = todayEpoch,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        val upcomingTask = Task(
            id = "t2",
            title = "Upcoming task",
            dueDateEpochDay = todayEpoch + 2,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        fakeTaskRepository.upsertTask(todayTask)
        fakeTaskRepository.upsertTask(upcomingTask)
        advanceUntilIdle()

        // Today filter
        viewModel.setFilter(TaskFilter.TODAY)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tasks.map { it.id }).contains("t1")
        assertThat(viewModel.uiState.value.tasks.map { it.id }).doesNotContain("t2")

        // Upcoming filter
        viewModel.setFilter(TaskFilter.UPCOMING)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tasks.map { it.id }).contains("t2")
        assertThat(viewModel.uiState.value.tasks.map { it.id }).doesNotContain("t1")
    }

    private class FakeTaskRepository : TaskRepository {
        private val tasks = MutableStateFlow<Map<String, Task>>(emptyMap())

        override fun getTasks(): Flow<List<Task>> = tasks.map { it.values.toList() }

        override fun getTasksForDate(epochDay: Long): Flow<List<Task>> {
            return tasks.map { map -> map.values.filter { it.dueDateEpochDay == epochDay } }
        }

        override fun getTaskById(id: String): Flow<Task?> = tasks.map { it[id] }

        override suspend fun upsertTask(task: Task) {
            tasks.update { it + (task.id to task) }
        }

        override suspend fun deleteTask(id: String) {
            tasks.update { it - id }
        }

        override suspend fun toggleTaskCompletion(id: String, completedAtEpochMs: Long?) {
            tasks.update { map ->
                val current = map[id] ?: return@update map
                val newCompleted = !current.isCompleted
                map + (id to current.copy(
                    isCompleted = newCompleted,
                    completedAtEpochMs = if (newCompleted) completedAtEpochMs else null
                ))
            }
        }

        override suspend fun getAllTasksDirect(): List<Task> = tasks.value.values.toList()

        override suspend fun deleteAllTasks() {
            tasks.value = emptyMap()
        }
    }

    private class FakePreferencesRepository : PreferencesRepository {
        private val _prefs = MutableStateFlow(UserPreferences())
        override fun getUserPreferences(): Flow<UserPreferences> = _prefs

        override suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {
            _prefs.update { it.copy(featureFlags = update(it.featureFlags)) }
        }

        override suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {}
        override suspend fun updateDarkTheme(theme: String) {}
        override suspend fun setOnboardingCompleted(completed: Boolean) {
            _prefs.update { it.copy(hasCompletedOnboarding = completed) }
        }
        override suspend fun clearPreferences() { _prefs.value = UserPreferences() }
    }

    private class FakeReminderScheduler : TaskReminderScheduler {
        val scheduledTasks = mutableListOf<Task>()
        val cancelledTasks = mutableListOf<String>()

        override fun scheduleReminder(task: Task) {
            scheduledTasks.add(task)
        }

        override fun cancelReminder(taskId: String) {
            cancelledTasks.add(taskId)
        }
    }
}

package com.orbit.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.ai.parser.NaturalLanguageDateParser
import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.usecase.CreateTaskUseCase
import com.orbit.core.domain.usecase.DeleteTaskUseCase
import com.orbit.core.domain.usecase.ExtractTaskWithReviewUseCase
import com.orbit.core.domain.usecase.GetPreferencesUseCase
import com.orbit.core.domain.usecase.GetTasksUseCase
import com.orbit.core.domain.usecase.TaskFilter
import com.orbit.core.domain.usecase.ToggleTaskCompletionUseCase
import com.orbit.core.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val filter: TaskFilter = TaskFilter.TODAY,
    val tasks: List<Task> = emptyList(),
    val quickCaptureText: String = "",
    val isAiExtractionEnabled: Boolean = false,
    val reviewDraft: ExtractedTaskDraft? = null,
    val editingTask: Task? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

private data class LocalState(
    val quickCaptureText: String = "",
    val reviewDraft: ExtractedTaskDraft? = null,
    val editingTask: Task? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getTasksUseCase: GetTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val extractTaskWithReviewUseCase: ExtractTaskWithReviewUseCase,
    getPreferencesUseCase: GetPreferencesUseCase
) : ViewModel() {

    private val _filter = MutableStateFlow(TaskFilter.TODAY)
    private val _localState = MutableStateFlow(LocalState())

    private val _tasksFlow = _filter.flatMapLatest { currentFilter ->
        getTasksUseCase(filter = currentFilter)
    }

    val uiState: StateFlow<TodayUiState> = combine(
        _filter,
        _tasksFlow,
        getPreferencesUseCase(),
        _localState
    ) { filter, tasks, prefs, local ->
        TodayUiState(
            filter = filter,
            tasks = tasks,
            quickCaptureText = local.quickCaptureText,
            isAiExtractionEnabled = prefs.featureFlags.aiExtractionEnabled,
            reviewDraft = local.reviewDraft,
            editingTask = local.editingTask,
            statusMessage = local.statusMessage,
            errorMessage = local.errorMessage,
            isBusy = local.isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState()
    )

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun updateQuickCaptureText(text: String) {
        _localState.update { it.copy(quickCaptureText = text) }
    }

    /**
     * Fast task capture with deterministic offline natural language date/time parsing.
     */
    fun quickCaptureSubmit() {
        val input = _localState.value.quickCaptureText.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            val parsed = NaturalLanguageDateParser.parse(input)
            val result = createTaskUseCase(
                title = parsed.cleanedTitle,
                notes = "",
                dueDateEpochDay = parsed.epochDay ?: LocalDate.now().toEpochDay(),
                dueTimeMinuteOfDay = parsed.minuteOfDay,
                priority = Priority.MEDIUM,
                reminderAtEpochMs = null
            )
            when (result) {
                is OrbitResult.Success -> {
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            quickCaptureText = "",
                            statusMessage = "Task created"
                        )
                    }
                }
                is OrbitResult.Error -> {
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * AI-assisted task extraction.
     * Always shows a review screen before writing data.
     * If AI fails, original text is preserved.
     */
    fun startAiReviewExtraction() {
        val input = _localState.value.quickCaptureText.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            val isAiEnabled = uiState.value.isAiExtractionEnabled
            val result = extractTaskWithReviewUseCase(input, isAiEnabled)

            when (result) {
                is OrbitResult.Success -> {
                    // Show review screen before persisting
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            reviewDraft = result.data
                        )
                    }
                }
                is OrbitResult.Error -> {
                    // Preserve original input and display review draft
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            reviewDraft = ExtractedTaskDraft(
                                title = input,
                                originalUserInput = input
                            )
                        )
                    }
                }
            }
        }
    }

    fun confirmReviewDraft(
        title: String,
        notes: String,
        dueDateEpochDay: Long?,
        dueTimeMinuteOfDay: Int?,
        priority: Priority,
        reminderAtEpochMs: Long?
    ) {
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            val result = createTaskUseCase(
                title = title,
                notes = notes,
                dueDateEpochDay = dueDateEpochDay,
                dueTimeMinuteOfDay = dueTimeMinuteOfDay,
                priority = priority,
                reminderAtEpochMs = reminderAtEpochMs
            )
            when (result) {
                is OrbitResult.Success -> {
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            reviewDraft = null,
                            quickCaptureText = "",
                            statusMessage = "Task saved"
                        )
                    }
                }
                is OrbitResult.Error -> {
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun dismissReview() {
        // Dismiss review sheet while preserving quickCaptureText so input is not lost
        _localState.update { it.copy(reviewDraft = null) }
    }

    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            toggleTaskCompletionUseCase(taskId)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            deleteTaskUseCase(taskId)
            _localState.update { it.copy(statusMessage = "Task deleted") }
        }
    }

    fun selectTaskForEdit(task: Task) {
        _localState.update { it.copy(editingTask = task) }
    }

    fun saveEditedTask(
        title: String,
        notes: String,
        dueDateEpochDay: Long?,
        dueTimeMinuteOfDay: Int?,
        priority: Priority,
        reminderAtEpochMs: Long?
    ) {
        val current = _localState.value.editingTask ?: return
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            val updated = current.copy(
                title = title,
                notes = notes,
                dueDateEpochDay = dueDateEpochDay,
                dueTimeMinuteOfDay = dueTimeMinuteOfDay,
                priority = priority,
                reminderAtEpochMs = reminderAtEpochMs
            )
            val result = updateTaskUseCase(updated)
            when (result) {
                is OrbitResult.Success -> {
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            editingTask = null,
                            statusMessage = "Task updated"
                        )
                    }
                }
                is OrbitResult.Error -> {
                    _localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun dismissEdit() {
        _localState.update { it.copy(editingTask = null) }
    }

    fun clearStatus() {
        _localState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}

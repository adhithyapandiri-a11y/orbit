package com.orbit.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.ai.provider.AiPromptRequest
import com.orbit.core.ai.provider.AiProvider
import com.orbit.core.ai.provider.AiResponse
import com.orbit.core.ai.provider.StructuredActionRequest
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.repository.VideoInboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

sealed class ProposedAction {
    data class CreateTask(val draft: ExtractedTaskDraft) : ProposedAction()
    data class CreateNote(val title: String, val content: String) : ProposedAction()
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val proposedAction: ProposedAction? = null,
    val actionExecuted: Boolean = false
)

data class AiAssistantUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "Hello! I am your Orbit Assistant. I can help plan your schedule, capture tasks, and search your local library safely without sending your private data to external servers.",
            isFromUser = false
        )
    ),
    val inputText: String = "",
    val isThinking: Boolean = false,
    val activeProviderName: String = "Orbit Local Engine (Offline & Private)"
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val meetingNoteRepository: MeetingNoteRepository,
    private val calendarRepository: CalendarRepository,
    private val videoInboxRepository: VideoInboxRepository,
    private val aiProvider: AiProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiAssistantUiState(activeProviderName = aiProvider.displayName)
    )
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(userText: String = _uiState.value.inputText) {
        val prompt = userText.trim()
        if (prompt.isBlank()) return

        val userMessage = ChatMessage(text = prompt, isFromUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isThinking = true
            )
        }

        viewModelScope.launch {
            processAssistantResponse(prompt)
        }
    }

    private suspend fun processAssistantResponse(prompt: String) {
        val lower = prompt.lowercase()

        // 1. Productivity Summary check
        if (lower.contains("summarize") || lower.contains("summary")) {
            val tasks = taskRepository.getAllTasksDirect()
            val pending = tasks.count { !it.isCompleted }
            val completed = tasks.count { it.isCompleted }
            val notes = meetingNoteRepository.getAllNotesDirect()
            val videos = videoInboxRepository.getAllVideosDirect()

            val text = buildString {
                appendLine("Here is your Orbit productivity summary:")
                appendLine("• Tasks: ${tasks.size} total ($completed completed, $pending pending)")
                appendLine("• Meeting Notes: ${notes.size} saved")
                appendLine("• Video Inbox: ${videos.size} saved")
                append("Keep up the great momentum!")
            }

            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(text = text, isFromUser = false),
                    isThinking = false
                )
            }
            return
        }

        // 2. Delegate to AiProvider
        val response = aiProvider.generateResponse(AiPromptRequest(prompt = prompt))
        val assistantMessage: ChatMessage = when (response) {
            is AiResponse.ActionProposal -> {
                when (val action = response.action) {
                    is StructuredActionRequest.CreateTask -> {
                        val draft = ExtractedTaskDraft(
                            title = action.title,
                            dueDateEpochDay = action.dueDateEpochDay,
                            dueTimeMinuteOfDay = action.dueTimeMinuteOfDay,
                            priority = action.priority,
                            notes = action.notes,
                            originalUserInput = prompt
                        )
                        ChatMessage(
                            text = "${response.explanation}\n• Title: ${action.title}\n• Priority: ${action.priority}\nWould you like me to add it to your tasks?",
                            isFromUser = false,
                            proposedAction = ProposedAction.CreateTask(draft)
                        )
                    }
                    is StructuredActionRequest.CreateNote -> {
                        ChatMessage(
                            text = "${response.explanation}\n• Title: ${action.title}\nWould you like me to save it locally?",
                            isFromUser = false,
                            proposedAction = ProposedAction.CreateNote(
                                title = action.title,
                                content = action.content
                            )
                        )
                    }
                    is StructuredActionRequest.SearchNotes -> {
                        val query = action.query.trim().lowercase()
                        val allNotes = meetingNoteRepository.getAllNotesDirect()
                        val matching = allNotes.filter {
                            it.title.lowercase().contains(query) || it.content.lowercase().contains(query)
                        }
                        val resultText = if (matching.isEmpty()) {
                            "No saved notes found matching \"${action.query}\"."
                        } else {
                            buildString {
                                appendLine("Found ${matching.size} matching note(s):")
                                matching.take(5).forEach { note ->
                                    val snippet = note.content.lines().firstOrNull()?.take(50) ?: ""
                                    appendLine("• ${note.title}: $snippet")
                                }
                            }.trimEnd()
                        }
                        ChatMessage(text = resultText, isFromUser = false)
                    }
                    is StructuredActionRequest.SearchVideos -> {
                        val query = action.query.trim().lowercase()
                        val allVideos = videoInboxRepository.getAllVideosDirect()
                        val matching = if (query.isBlank()) allVideos else allVideos.filter {
                            it.title.lowercase().contains(query) ||
                                it.notes.lowercase().contains(query) ||
                                it.tags.any { tag -> tag.lowercase().contains(query) }
                        }
                        val resultText = if (matching.isEmpty()) {
                            "No videos found matching \"${action.query}\"."
                        } else {
                            buildString {
                                appendLine("Found ${matching.size} video(s):")
                                matching.take(5).forEach { video ->
                                    appendLine("• ${video.title}")
                                }
                            }.trimEnd()
                        }
                        ChatMessage(text = resultText, isFromUser = false)
                    }
                    is StructuredActionRequest.QueryCalendar -> {
                        val targetDay = action.queryDateEpochDay ?: LocalDate.now().toEpochDay()
                        val targetDate = LocalDate.ofEpochDay(targetDay)
                        val startOfDayMs = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endOfDayMs = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                        val allEvents = calendarRepository.getAllEventsDirect()
                        val dayEvents = allEvents.filter {
                            it.startEpochMs in startOfDayMs until endOfDayMs &&
                                !it.id.startsWith("event_note_") &&
                                !it.id.startsWith("note_snapshot_") &&
                                it.description != "Linked Orbit Note"
                        }
                        val dayTasks = taskRepository.getAllTasksDirect().filter {
                            it.dueDateEpochDay == targetDay
                        }

                        val resultText = buildString {
                            appendLine("Schedule for $targetDate:")
                            if (dayEvents.isEmpty() && dayTasks.isEmpty()) {
                                appendLine("• No calendar events or tasks scheduled.")
                            } else {
                                if (dayEvents.isNotEmpty()) {
                                    appendLine("Events (${dayEvents.size}):")
                                    dayEvents.forEach { ev ->
                                        appendLine("  • ${ev.title}")
                                    }
                                }
                                if (dayTasks.isNotEmpty()) {
                                    appendLine("Tasks (${dayTasks.size}):")
                                    dayTasks.forEach { tk ->
                                        val status = if (tk.isCompleted) "[✓]" else "[ ]"
                                        appendLine("  $status ${tk.title}")
                                    }
                                }
                            }
                        }.trimEnd()
                        ChatMessage(text = resultText, isFromUser = false)
                    }
                }
            }
            is AiResponse.Text -> {
                ChatMessage(text = response.content, isFromUser = false)
            }
            is AiResponse.Error -> {
                ChatMessage(text = "Error: ${response.message}", isFromUser = false)
            }
        }

        _uiState.update {
            it.copy(
                messages = it.messages + assistantMessage,
                isThinking = false
            )
        }
    }

    fun executeAction(messageId: String, action: ProposedAction) {
        viewModelScope.launch {
            when (action) {
                is ProposedAction.CreateTask -> {
                    val draft = action.draft
                    val now = System.currentTimeMillis()
                    val task = Task(
                        id = UUID.randomUUID().toString(),
                        title = draft.title,
                        notes = "Added via AI Assistant",
                        dueDateEpochDay = draft.dueDateEpochDay ?: LocalDate.now().toEpochDay(),
                        dueTimeMinuteOfDay = draft.dueTimeMinuteOfDay,
                        priority = draft.priority,
                        isCompleted = false,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                    taskRepository.upsertTask(task)
                }
                is ProposedAction.CreateNote -> {
                    val now = System.currentTimeMillis()
                    val noteId = UUID.randomUUID().toString()
                    val eventId = "event_note_$noteId"
                    calendarRepository.upsertEvents(
                        listOf(
                            CalendarEventSnapshot(
                                id = eventId,
                                calendarId = 0,
                                externalEventId = 0,
                                title = action.title,
                                description = "Linked Orbit Note",
                                startEpochMs = now,
                                endEpochMs = now + 3600000,
                                lastSyncedAtEpochMs = now
                            )
                        )
                    )
                    val note = MeetingNote(
                        id = noteId,
                        eventSnapshotId = eventId,
                        title = action.title,
                        content = action.content,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                    meetingNoteRepository.upsertNote(note)
                }
            }

            // Mark message action as executed
            _uiState.update { state ->
                val updated = state.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(actionExecuted = true) else msg
                }
                state.copy(messages = updated)
            }
        }
    }
}

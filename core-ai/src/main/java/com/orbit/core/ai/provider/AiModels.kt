package com.orbit.core.ai.provider

import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.domain.model.Priority
import kotlinx.serialization.Serializable

@Serializable
sealed class StructuredActionRequest {
    abstract val actionName: String

    @Serializable
    data class CreateTask(
        val title: String,
        val notes: String = "",
        val dueDateEpochDay: Long? = null,
        val dueTimeMinuteOfDay: Int? = null,
        val priority: Priority = Priority.MEDIUM
    ) : StructuredActionRequest() {
        override val actionName: String = "create_task"
    }

    @Serializable
    data class CreateNote(
        val title: String,
        val content: String,
        val category: String = "General"
    ) : StructuredActionRequest() {
        override val actionName: String = "create_note"
    }

    @Serializable
    data class SearchNotes(
        val query: String
    ) : StructuredActionRequest() {
        override val actionName: String = "search_notes"
    }

    @Serializable
    data class QueryCalendar(
        val queryDateEpochDay: Long? = null,
        val query: String = ""
    ) : StructuredActionRequest() {
        override val actionName: String = "query_calendar"
    }

    @Serializable
    data class SearchVideos(
        val query: String
    ) : StructuredActionRequest() {
        override val actionName: String = "search_videos"
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

object ActionValidator {
    fun validate(action: StructuredActionRequest): ValidationResult {
        return when (action) {
            is StructuredActionRequest.CreateTask -> {
                if (action.title.isBlank()) {
                    ValidationResult.Invalid("Task title cannot be blank")
                } else if (action.title.length > 200) {
                    ValidationResult.Invalid("Task title cannot exceed 200 characters")
                } else {
                    ValidationResult.Valid
                }
            }
            is StructuredActionRequest.CreateNote -> {
                if (action.title.isBlank()) {
                    ValidationResult.Invalid("Note title cannot be blank")
                } else {
                    ValidationResult.Valid
                }
            }
            is StructuredActionRequest.SearchNotes -> {
                if (action.query.isBlank()) {
                    ValidationResult.Invalid("Search query cannot be blank")
                } else {
                    ValidationResult.Valid
                }
            }
            is StructuredActionRequest.QueryCalendar -> ValidationResult.Valid
            is StructuredActionRequest.SearchVideos -> {
                if (action.query.isBlank()) {
                    ValidationResult.Invalid("Search query cannot be blank")
                } else {
                    ValidationResult.Valid
                }
            }
        }
    }
}

data class AiConversationEntry(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiPromptRequest(
    val prompt: String,
    val conversationHistory: List<AiConversationEntry> = emptyList()
)

sealed class AiResponse {
    data class Text(val content: String) : AiResponse()
    data class ActionProposal(
        val explanation: String,
        val action: StructuredActionRequest,
        val requiresUserConfirmation: Boolean = true
    ) : AiResponse()
    data class Error(val message: String, val canRetry: Boolean = false) : AiResponse()
}

interface AiProvider {
    val id: String
    val displayName: String
    val isLocal: Boolean
    suspend fun generateResponse(request: AiPromptRequest): AiResponse
}

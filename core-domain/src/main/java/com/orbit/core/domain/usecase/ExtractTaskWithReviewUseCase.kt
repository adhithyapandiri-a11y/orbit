package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.domain.model.OrbitErrorCode
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.TaskExtractor

class ExtractTaskWithReviewUseCase(
    private val taskExtractor: TaskExtractor
) {
    suspend operator fun invoke(
        rawInput: String,
        isAiExtractionEnabled: Boolean
    ): OrbitResult<ExtractedTaskDraft> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return OrbitResult.Error(
                code = OrbitErrorCode.VALIDATION_ERROR,
                message = "Task input cannot be blank"
            )
        }

        return try {
            val draft = taskExtractor.extract(trimmed)
            OrbitResult.Success(draft)
        } catch (_: Exception) {
            // Preservation rule: If AI is unavailable, malformed, or errors, preserve raw user text
            OrbitResult.Success(
                ExtractedTaskDraft(
                    title = trimmed,
                    notes = "",
                    dueDateEpochDay = null,
                    dueTimeMinuteOfDay = null,
                    priority = Priority.MEDIUM,
                    originalUserInput = trimmed
                )
            )
        }
    }
}

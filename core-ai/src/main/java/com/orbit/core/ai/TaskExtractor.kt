package com.orbit.core.ai

import com.orbit.core.ai.parser.NaturalLanguageDateParser
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import java.time.LocalDate
import java.util.UUID

data class ExtractedTaskDraft(
    val title: String,
    val notes: String = "",
    val dueDateEpochDay: Long? = null,
    val dueTimeMinuteOfDay: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    val originalUserInput: String
) {
    fun toTask(): Task {
        val now = System.currentTimeMillis()
        return Task(
            id = UUID.randomUUID().toString(),
            title = title,
            notes = notes,
            dueDateEpochDay = dueDateEpochDay,
            dueTimeMinuteOfDay = dueTimeMinuteOfDay,
            priority = priority,
            isCompleted = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }
}

interface TaskExtractor {
    suspend fun extract(rawInput: String): ExtractedTaskDraft
}

class OfflineDeterministicTaskExtractor(
    private val dateSupplier: () -> LocalDate = { LocalDate.now() }
) : TaskExtractor {

    override suspend fun extract(rawInput: String): ExtractedTaskDraft {
        val parsed = NaturalLanguageDateParser.parse(rawInput, dateSupplier())
        val priority = when {
            rawInput.contains("!urgent", ignoreCase = true) || rawInput.contains("urgent", ignoreCase = true) -> Priority.URGENT
            rawInput.contains("!high", ignoreCase = true) -> Priority.HIGH
            rawInput.contains("!low", ignoreCase = true) -> Priority.LOW
            else -> Priority.MEDIUM
        }
        val cleanTitle = parsed.cleanedTitle
            .replace("(?i)!urgent|!high|!low".toRegex(), "")
            .trim()

        return ExtractedTaskDraft(
            title = if (cleanTitle.isNotBlank()) cleanTitle else rawInput.trim(),
            notes = "",
            dueDateEpochDay = parsed.epochDay,
            dueTimeMinuteOfDay = parsed.minuteOfDay,
            priority = priority,
            originalUserInput = rawInput
        )
    }
}

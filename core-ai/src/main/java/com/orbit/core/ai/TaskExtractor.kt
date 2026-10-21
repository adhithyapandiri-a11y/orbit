package com.orbit.core.ai

import com.orbit.core.ai.parser.NaturalLanguageDateParser
import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.TaskExtractor
import java.time.LocalDate

class OfflineDeterministicTaskExtractor(
    private val dateSupplier: () -> LocalDate = { LocalDate.now() }
) : TaskExtractor {

    private val URGENT_PATTERN = "(?i)(?:!urgent|\\burgent\\b)".toRegex()
    private val HIGH_PATTERN = "(?i)(?:!high|\\bhigh priority\\b)".toRegex()
    private val LOW_PATTERN = "(?i)(?:!low|\\blow priority\\b)".toRegex()
    private val MEDIUM_PATTERN = "(?i)(?:!medium|!med|\\bmedium priority\\b)".toRegex()

    override suspend fun extract(rawInput: String): ExtractedTaskDraft {
        return try {
            val parsed = NaturalLanguageDateParser.parse(rawInput, dateSupplier())
            val priority = when {
                URGENT_PATTERN.containsMatchIn(rawInput) -> Priority.URGENT
                HIGH_PATTERN.containsMatchIn(rawInput) -> Priority.HIGH
                LOW_PATTERN.containsMatchIn(rawInput) -> Priority.LOW
                else -> Priority.MEDIUM
            }
            val cleanTitle = parsed.cleanedTitle
                .replace(URGENT_PATTERN, "")
                .replace(HIGH_PATTERN, "")
                .replace(LOW_PATTERN, "")
                .replace(MEDIUM_PATTERN, "")
                .replace("\\s+".toRegex(), " ")
                .trim()

            ExtractedTaskDraft(
                title = if (cleanTitle.isNotBlank()) cleanTitle else rawInput.trim(),
                notes = "",
                dueDateEpochDay = parsed.epochDay,
                dueTimeMinuteOfDay = parsed.minuteOfDay,
                priority = priority,
                originalUserInput = rawInput
            )
        } catch (_: Exception) {
            // If AI is unavailable, malformed, or errors, preserve user text and keep task flow functional
            ExtractedTaskDraft(
                title = rawInput.trim(),
                notes = "",
                dueDateEpochDay = null,
                dueTimeMinuteOfDay = null,
                priority = Priority.MEDIUM,
                originalUserInput = rawInput
            )
        }
    }
}

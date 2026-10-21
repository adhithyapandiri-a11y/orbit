package com.orbit.core.ai.provider

import com.orbit.core.ai.OfflineDeterministicTaskExtractor
import com.orbit.core.domain.model.TaskExtractor
import com.orbit.core.ai.parser.NaturalLanguageDateParser
import java.time.LocalDate

class OfflineDeterministicAiProvider(
    private val taskExtractor: TaskExtractor = OfflineDeterministicTaskExtractor(),
    private val dateSupplier: () -> LocalDate = { LocalDate.now() }
) : AiProvider {

    override val id: String = "orbit_offline_local"
    override val displayName: String = "Orbit Local Engine (Offline & Private)"
    override val isLocal: Boolean = true

    override suspend fun generateResponse(request: AiPromptRequest): AiResponse {
        val prompt = request.prompt.trim()
        if (prompt.isBlank()) {
            return AiResponse.Text("How can I help you organize your tasks, notes, or schedule today?")
        }

        val lower = prompt.lowercase()

        // 1. Search Notes intent
        if (lower.contains("search note") || lower.contains("find note") || lower.contains("lookup note")) {
            val query = prompt
                .replace("(?i)search\\s+notes?\\s+(?:for\\s+)?".toRegex(), "")
                .replace("(?i)find\\s+notes?\\s+(?:about\\s+)?".toRegex(), "")
                .replace("(?i)lookup\\s+notes?\\s+(?:for\\s+)?".toRegex(), "")
                .trim()
                .ifBlank { prompt }
            val action = StructuredActionRequest.SearchNotes(query = query)
            return AiResponse.ActionProposal(
                explanation = "I can search your private local notes for \"$query\":",
                action = action,
                requiresUserConfirmation = false
            )
        }

        // 2. Search Videos / MyTube intent
        if (lower.contains("search video") || lower.contains("find video") || lower.contains("my videos") || lower.contains("saved videos")) {
            val query = prompt
                .replace("(?i)search\\s+videos?\\s+(?:for\\s+)?".toRegex(), "")
                .replace("(?i)find\\s+videos?\\s+(?:about\\s+)?".toRegex(), "")
                .replace("(?i)my\\s+videos?\\s+(?:about\\s+)?".toRegex(), "")
                .replace("(?i)saved\\s+videos?\\s+(?:about\\s+)?".toRegex(), "")
                .trim()
            val action = StructuredActionRequest.SearchVideos(query = query)
            return AiResponse.ActionProposal(
                explanation = if (query.isNotBlank()) "I can search your video inbox for \"$query\":" else "Here are your saved videos:",
                action = action,
                requiresUserConfirmation = false
            )
        }

        // 3. Query Calendar / Agenda intent
        if (lower.contains("calendar") || lower.contains("agenda") || lower.contains("schedule") || lower.contains("meetings")) {
            val parsedDate = NaturalLanguageDateParser.parse(prompt, dateSupplier())
            val targetDay = parsedDate.epochDay ?: dateSupplier().toEpochDay()
            val action = StructuredActionRequest.QueryCalendar(
                queryDateEpochDay = targetDay,
                query = prompt
            )
            return AiResponse.ActionProposal(
                explanation = "Checking your agenda and schedule:",
                action = action,
                requiresUserConfirmation = false
            )
        }

        // 4. Create Note intent
        if (lower.contains("save note") || lower.contains("create note") || lower.contains("write note") || lower.contains("take note")) {
            val cleanContent = prompt
                .replace("(?i)^(?:save|create|write|take)\\s+note\\s*:?\\s*".toRegex(), "")
                .trim()
            val titleCandidate = cleanContent.take(32).substringBefore("\n").trim().ifBlank { "Personal Note" }
            val action = StructuredActionRequest.CreateNote(
                title = titleCandidate.replaceFirstChar { it.uppercase() },
                content = cleanContent.ifBlank { prompt },
                category = "General"
            )
            val validation = ActionValidator.validate(action)
            return if (validation is ValidationResult.Valid) {
                AiResponse.ActionProposal(
                    explanation = "I can save this note for you:\n• Title: ${action.title}\nWould you like me to store it in your local notes?",
                    action = action,
                    requiresUserConfirmation = true
                )
            } else {
                AiResponse.Error("Invalid note content: ${(validation as ValidationResult.Invalid).reason}")
            }
        }

        // 5. Create Task intent
        if (lower.contains("task") || lower.contains("remind") || lower.contains("todo") || lower.startsWith("add ") || lower.contains("plan to ")) {
            val cleanTaskInput = prompt
                .replace("(?i)^add\\s+tasks?\\s*:?\\s*".toRegex(), "")
                .replace("(?i)^remind\\s+(?:me\\s+)?(?:to\\s+)?".toRegex(), "")
                .replace("(?i)^todo\\s*:?\\s*".toRegex(), "")
                .trim()
                .ifBlank { prompt }
            val draft = taskExtractor.extract(cleanTaskInput)
            val action = StructuredActionRequest.CreateTask(
                title = draft.title.replaceFirstChar { it.uppercase() },
                notes = "Created via Orbit AI Assistant",
                dueDateEpochDay = draft.dueDateEpochDay ?: dateSupplier().toEpochDay(),
                dueTimeMinuteOfDay = draft.dueTimeMinuteOfDay,
                priority = draft.priority
            )
            val validation = ActionValidator.validate(action)
            return if (validation is ValidationResult.Valid) {
                AiResponse.ActionProposal(
                    explanation = "I've structured a task for you:\n• Title: ${action.title}\n• Priority: ${action.priority}\nWould you like me to add it to your tasks?",
                    action = action,
                    requiresUserConfirmation = true
                )
            } else {
                AiResponse.Error("Could not create task: ${(validation as ValidationResult.Invalid).reason}")
            }
        }

        // 6. Summary intent
        if (lower.contains("summary") || lower.contains("summarize") || lower.contains("overview") || lower.contains("progress")) {
            return AiResponse.Text("REQUEST_SUMMARY")
        }

        // 7. General conversational guidance
        return AiResponse.Text(
            "I received your message. You can ask me to:\n" +
            "1. 'Add task: finish quarterly report tomorrow at 3pm !urgent'\n" +
            "2. 'Save note: architecture design for offline AI sync'\n" +
            "3. 'Search notes for project'\n" +
            "4. 'What's on my calendar today?'\n" +
            "5. 'Search videos for kotlin'\n" +
            "6. 'Summarize my day'"
        )
    }
}

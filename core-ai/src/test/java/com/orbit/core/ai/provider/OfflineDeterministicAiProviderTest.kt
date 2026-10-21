package com.orbit.core.ai.provider

import com.google.common.truth.Truth.assertThat
import com.orbit.core.ai.OfflineDeterministicTaskExtractor
import com.orbit.core.domain.model.Priority
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class OfflineDeterministicAiProviderTest {

    private val referenceDate = LocalDate.of(2026, 10, 20)
    private val taskExtractor = OfflineDeterministicTaskExtractor { referenceDate }
    private val provider = OfflineDeterministicAiProvider(taskExtractor) { referenceDate }

    @Test
    fun generateResponse_taskPrompt_generatesCreateTaskActionWithConfirmation() = runTest {
        val request = AiPromptRequest("Add task: Submit taxes tomorrow at 2pm !urgent")
        val response = provider.generateResponse(request)

        assertThat(response).isInstanceOf(AiResponse.ActionProposal::class.java)
        val proposal = response as AiResponse.ActionProposal
        assertThat(proposal.requiresUserConfirmation).isTrue()
        assertThat(proposal.action).isInstanceOf(StructuredActionRequest.CreateTask::class.java)

        val taskAction = proposal.action as StructuredActionRequest.CreateTask
        assertThat(taskAction.title).isEqualTo("Submit taxes")
        assertThat(taskAction.priority).isEqualTo(Priority.URGENT)
        assertThat(taskAction.dueDateEpochDay).isEqualTo(referenceDate.plusDays(1).toEpochDay())
        assertThat(taskAction.dueTimeMinuteOfDay).isEqualTo(14 * 60)
    }

    @Test
    fun generateResponse_notePrompt_generatesCreateNoteAction() = runTest {
        val request = AiPromptRequest("Save note: Key takeaways from meeting with investors")
        val response = provider.generateResponse(request)

        assertThat(response).isInstanceOf(AiResponse.ActionProposal::class.java)
        val proposal = response as AiResponse.ActionProposal
        assertThat(proposal.requiresUserConfirmation).isTrue()
        assertThat(proposal.action).isInstanceOf(StructuredActionRequest.CreateNote::class.java)

        val noteAction = proposal.action as StructuredActionRequest.CreateNote
        assertThat(noteAction.title).contains("Key takeaways")
        assertThat(noteAction.content).contains("investors")
    }

    @Test
    fun generateResponse_searchNotes_generatesSearchNotesActionWithoutConfirmation() = runTest {
        val request = AiPromptRequest("Search notes for sprint roadmap")
        val response = provider.generateResponse(request)

        assertThat(response).isInstanceOf(AiResponse.ActionProposal::class.java)
        val proposal = response as AiResponse.ActionProposal
        assertThat(proposal.requiresUserConfirmation).isFalse()
        assertThat(proposal.action).isInstanceOf(StructuredActionRequest.SearchNotes::class.java)

        val searchAction = proposal.action as StructuredActionRequest.SearchNotes
        assertThat(searchAction.query).isEqualTo("sprint roadmap")
    }

    @Test
    fun generateResponse_searchVideos_generatesSearchVideosAction() = runTest {
        val request = AiPromptRequest("Search videos for jetpack compose")
        val response = provider.generateResponse(request)

        assertThat(response).isInstanceOf(AiResponse.ActionProposal::class.java)
        val proposal = response as AiResponse.ActionProposal
        assertThat(proposal.requiresUserConfirmation).isFalse()
        assertThat(proposal.action).isInstanceOf(StructuredActionRequest.SearchVideos::class.java)

        val videoAction = proposal.action as StructuredActionRequest.SearchVideos
        assertThat(videoAction.query).isEqualTo("jetpack compose")
    }

    @Test
    fun generateResponse_calendarQuery_generatesQueryCalendarAction() = runTest {
        val request = AiPromptRequest("What is on my calendar tomorrow?")
        val response = provider.generateResponse(request)

        assertThat(response).isInstanceOf(AiResponse.ActionProposal::class.java)
        val proposal = response as AiResponse.ActionProposal
        assertThat(proposal.requiresUserConfirmation).isFalse()
        assertThat(proposal.action).isInstanceOf(StructuredActionRequest.QueryCalendar::class.java)

        val calAction = proposal.action as StructuredActionRequest.QueryCalendar
        assertThat(calAction.queryDateEpochDay).isEqualTo(referenceDate.plusDays(1).toEpochDay())
    }

    @Test
    fun generateResponse_emptyInput_returnsSafeHelpText() = runTest {
        val request = AiPromptRequest("   ")
        val response = provider.generateResponse(request)

        assertThat(response).isInstanceOf(AiResponse.Text::class.java)
        val text = response as AiResponse.Text
        assertThat(text.content).contains("How can I help you")
    }

    @Test
    fun actionValidator_blankTitle_rejects() {
        val invalidTask = StructuredActionRequest.CreateTask(title = "   ")
        assertThat(ActionValidator.validate(invalidTask)).isInstanceOf(ValidationResult.Invalid::class.java)

        val invalidNote = StructuredActionRequest.CreateNote(title = "", content = "Some content")
        assertThat(ActionValidator.validate(invalidNote)).isInstanceOf(ValidationResult.Invalid::class.java)

        val validTask = StructuredActionRequest.CreateTask(title = "Valid title")
        assertThat(ActionValidator.validate(validTask)).isEqualTo(ValidationResult.Valid)
    }
}

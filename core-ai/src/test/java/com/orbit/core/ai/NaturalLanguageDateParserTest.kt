package com.orbit.core.ai

import com.orbit.core.ai.parser.NaturalLanguageDateParser
import com.orbit.core.domain.model.Priority
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class NaturalLanguageDateParserTest {

    private val referenceDate = LocalDate.of(2026, 10, 20) // Tuesday

    @Test
    fun parse_tomorrowAt5pm_extractsCorrectDayAndTime() {
        val input = "Dentist appointment tomorrow at 5pm"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.plusDays(1).toEpochDay())
        assertThat(parsed.minuteOfDay).isEqualTo(17 * 60) // 17:00
        assertThat(parsed.cleanedTitle).isEqualTo("Dentist appointment")
    }

    @Test
    fun parse_in3Days_extractsDate() {
        val input = "Pay electricity bill in 3 days"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.plusDays(3).toEpochDay())
        assertThat(parsed.cleanedTitle).isEqualTo("Pay electricity bill")
    }

    @Test
    fun parse_24hFormat_extractsTime() {
        val input = "Team standup today at 09:30"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.toEpochDay())
        assertThat(parsed.minuteOfDay).isEqualTo(9 * 60 + 30)
        assertThat(parsed.cleanedTitle).isEqualTo("Team standup")
    }

    @Test
    fun offlineTaskExtractor_preservesOriginalInputAndExtractsUrgent() = runTest {
        val extractor = OfflineDeterministicTaskExtractor { referenceDate }
        val draft = extractor.extract("Server crash report tomorrow at 10am !urgent")

        assertThat(draft.originalUserInput).isEqualTo("Server crash report tomorrow at 10am !urgent")
        assertThat(draft.priority).isEqualTo(Priority.URGENT)
        assertThat(draft.dueDateEpochDay).isEqualTo(referenceDate.plusDays(1).toEpochDay())
        assertThat(draft.dueTimeMinuteOfDay).isEqualTo(10 * 60)
        assertThat(draft.title).isEqualTo("Server crash report")
    }
}

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
    fun parse_in2Weeks_extractsDate() {
        val input = "Renew passport in 2 weeks"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.plusWeeks(2).toEpochDay())
        assertThat(parsed.cleanedTitle).isEqualTo("Renew passport")
    }

    @Test
    fun parse_tonight_extractsTodayAndDefaultEveningTime() {
        val input = "Finish novel chapter tonight"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.toEpochDay())
        assertThat(parsed.minuteOfDay).isEqualTo(20 * 60) // 20:00 (8pm)
        assertThat(parsed.cleanedTitle).isEqualTo("Finish novel chapter")
    }

    @Test
    fun parse_weekdayWithOn_extractsUpcomingWeekday() {
        // referenceDate is Tuesday Oct 20 -> Upcoming Friday is Oct 23 (+3 days)
        val input = "Team retro on Friday at 4pm"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.plusDays(3).toEpochDay())
        assertThat(parsed.minuteOfDay).isEqualTo(16 * 60)
        assertThat(parsed.cleanedTitle).isEqualTo("Team retro")
    }

    @Test
    fun parse_weekdayWithThis_extractsUpcomingWeekday() {
        // referenceDate is Tuesday Oct 20 -> Upcoming Thursday is Oct 22 (+2 days)
        val input = "Doctor checkup this Thursday at 10am"
        val parsed = NaturalLanguageDateParser.parse(input, referenceDate)

        assertThat(parsed.epochDay).isEqualTo(referenceDate.plusDays(2).toEpochDay())
        assertThat(parsed.minuteOfDay).isEqualTo(10 * 60)
        assertThat(parsed.cleanedTitle).isEqualTo("Doctor checkup")
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

    @Test
    fun offlineTaskExtractor_wordBoundaryCheck_detergentDoesNotTriggerUrgent() = runTest {
        val extractor = OfflineDeterministicTaskExtractor { referenceDate }
        val draft = extractor.extract("Buy laundry detergent today")

        assertThat(draft.originalUserInput).isEqualTo("Buy laundry detergent today")
        assertThat(draft.priority).isEqualTo(Priority.MEDIUM)
        assertThat(draft.title).isEqualTo("Buy laundry detergent")
        assertThat(draft.dueDateEpochDay).isEqualTo(referenceDate.toEpochDay())
    }
}

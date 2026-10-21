package com.orbit.core.ai.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.regex.Pattern

data class ParsedDateTime(
    val epochDay: Long?,
    val minuteOfDay: Int?,
    val cleanedTitle: String
)

object NaturalLanguageDateParser {

    private val TIME_PATTERN_12H = Pattern.compile(
        "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val TIME_PATTERN_24H = Pattern.compile(
        "\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val IN_DAYS_PATTERN = Pattern.compile(
        "\\bin\\s+(\\d+)\\s+days?\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val IN_WEEKS_PATTERN = Pattern.compile(
        "\\bin\\s+(\\d+)\\s+weeks?\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val WEEKDAY_MAP: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY,
        "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "tue" to DayOfWeek.TUESDAY,
        "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY,
        "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "sun" to DayOfWeek.SUNDAY
    )

    fun parse(input: String, referenceDate: LocalDate = LocalDate.now()): ParsedDateTime {
        var text = input
        var date: LocalDate? = null
        var time: LocalTime? = null
        var isTonight = false

        // 1. Detect "in X days"
        val inDaysMatcher = IN_DAYS_PATTERN.matcher(text)
        if (inDaysMatcher.find()) {
            val days = inDaysMatcher.group(1)?.toLongOrNull() ?: 0L
            date = referenceDate.plusDays(days)
            text = inDaysMatcher.replaceFirst("").trim()
        }

        // 2. Detect "in X weeks"
        if (date == null) {
            val inWeeksMatcher = IN_WEEKS_PATTERN.matcher(text)
            if (inWeeksMatcher.find()) {
                val weeks = inWeeksMatcher.group(1)?.toLongOrNull() ?: 0L
                date = referenceDate.plusWeeks(weeks)
                text = inWeeksMatcher.replaceFirst("").trim()
            }
        }

        // 3. Detect "today", "tonight", "tomorrow", "day after tomorrow"
        if (date == null) {
            val lower = text.lowercase(Locale.ROOT)
            when {
                lower.contains("day after tomorrow") -> {
                    date = referenceDate.plusDays(2)
                    text = text.replace("(?i)\\bday after tomorrow\\b".toRegex(), "").trim()
                }
                lower.contains("tomorrow") -> {
                    date = referenceDate.plusDays(1)
                    text = text.replace("(?i)\\btomorrow\\b".toRegex(), "").trim()
                }
                lower.contains("tonight") -> {
                    date = referenceDate
                    isTonight = true
                    text = text.replace("(?i)\\btonight\\b".toRegex(), "").trim()
                }
                lower.contains("today") -> {
                    date = referenceDate
                    text = text.replace("(?i)\\btoday\\b".toRegex(), "").trim()
                }
            }
        }

        // 4. Detect "(on/this/next) <weekday>"
        if (date == null) {
            for ((key, targetDayOfWeek) in WEEKDAY_MAP) {
                val pattern = "(?i)\\b(?:(next|this|on)\\s+)?$key\\b".toRegex()
                val match = pattern.find(text)
                if (match != null) {
                    val modifier = match.groups[1]?.value?.lowercase(Locale.ROOT)
                    var target = referenceDate.plusDays(1)
                    while (target.dayOfWeek != targetDayOfWeek) {
                        target = target.plusDays(1)
                    }
                    if (modifier == "next" && targetDayOfWeek == referenceDate.dayOfWeek) {
                        target = referenceDate.plusWeeks(1)
                    }
                    date = target
                    text = text.replaceRange(match.range, "").trim()
                    break
                }
            }
        }

        // 5. Detect 12h time (e.g., "at 5pm", "3:30 pm")
        val matcher12h = TIME_PATTERN_12H.matcher(text)
        if (matcher12h.find()) {
            val hourRaw = matcher12h.group(1)?.toIntOrNull() ?: 0
            val minuteRaw = matcher12h.group(2)?.toIntOrNull() ?: 0
            val amPm = matcher12h.group(3)?.lowercase(Locale.ROOT) ?: "am"
            var hour = hourRaw
            if (amPm == "pm" && hour in 1..11) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            time = LocalTime.of(hour.coerceIn(0, 23), minuteRaw.coerceIn(0, 59))
            text = matcher12h.replaceFirst("").trim()
        } else {
            // 6. Detect 24h time (e.g., "at 17:00", "09:30")
            val matcher24h = TIME_PATTERN_24H.matcher(text)
            if (matcher24h.find()) {
                val hour = matcher24h.group(1)?.toIntOrNull() ?: 0
                val minute = matcher24h.group(2)?.toIntOrNull() ?: 0
                time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                text = matcher24h.replaceFirst("").trim()
            } else if (isTonight) {
                // If "tonight" was parsed without explicit time, default to 20:00 (8pm)
                time = LocalTime.of(20, 0)
            }
        }

        // Clean up remaining filler words (e.g. trailing "at", "on", extra spaces)
        val cleaned = text
            .replace("(?i)\\b(?:at|on)\\s*$".toRegex(), "")
            .replace("^\\s*(?:at|on)\\b".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return ParsedDateTime(
            epochDay = date?.toEpochDay(),
            minuteOfDay = time?.let { it.hour * 60 + it.minute },
            cleanedTitle = if (cleaned.isNotBlank()) cleaned else input.trim()
        )
    }
}

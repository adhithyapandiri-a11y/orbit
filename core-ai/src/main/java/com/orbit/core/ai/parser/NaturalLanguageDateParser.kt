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

    fun parse(input: String, referenceDate: LocalDate = LocalDate.now()): ParsedDateTime {
        var text = input
        var date: LocalDate? = null
        var time: LocalTime? = null

        // 1. Detect "in X days"
        val inDaysMatcher = IN_DAYS_PATTERN.matcher(text)
        if (inDaysMatcher.find()) {
            val days = inDaysMatcher.group(1)?.toLongOrNull() ?: 0L
            date = referenceDate.plusDays(days)
            text = inDaysMatcher.replaceFirst("").trim()
        }

        // 2. Detect "today", "tomorrow", "day after tomorrow"
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
                lower.contains("today") -> {
                    date = referenceDate
                    text = text.replace("(?i)\\btoday\\b".toRegex(), "").trim()
                }
            }
        }

        // 3. Detect "next <weekday>" or "<weekday>"
        if (date == null) {
            for (dayOfWeek in DayOfWeek.entries) {
                val dayName = dayOfWeek.name.lowercase(Locale.ROOT)
                val patternNext = "(?i)\\bnext\\s+$dayName\\b".toRegex()
                if (patternNext.containsMatchIn(text)) {
                    var target = referenceDate.plusDays(1)
                    while (target.dayOfWeek != dayOfWeek) {
                        target = target.plusDays(1)
                    }
                    date = target
                    text = text.replace(patternNext, "").trim()
                    break
                }
            }
        }

        // 4. Detect 12h time (e.g., "at 5pm", "3:30 pm")
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
            // 5. Detect 24h time (e.g., "at 17:00", "09:30")
            val matcher24h = TIME_PATTERN_24H.matcher(text)
            if (matcher24h.find()) {
                val hour = matcher24h.group(1)?.toIntOrNull() ?: 0
                val minute = matcher24h.group(2)?.toIntOrNull() ?: 0
                time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                text = matcher24h.replaceFirst("").trim()
            }
        }

        // Clean up remaining filler words (e.g. trailing "at", extra spaces)
        val cleaned = text
            .replace("(?i)\\bat\\s*$".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return ParsedDateTime(
            epochDay = date?.toEpochDay(),
            minuteOfDay = time?.let { it.hour * 60 + it.minute },
            cleanedTitle = if (cleaned.isNotBlank()) cleaned else input.trim()
        )
    }
}

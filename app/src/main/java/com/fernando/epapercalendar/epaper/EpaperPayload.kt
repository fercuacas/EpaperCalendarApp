package com.fernando.epapercalendar.epaper

import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.CalendarOccurrence
import com.fernando.epapercalendar.calendar.EventCategory
import com.fernando.epapercalendar.calendar.RecurrenceRule
import com.fernando.epapercalendar.calendar.customWeekdayOccurrences
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val EPAPER_MAX_PAYLOAD_BYTES = 96 * 1024
const val EPAPER_MAX_OCCURRENCES = 768

data class EpaperPayloadResult(
    val json: String,
    val occurrenceCount: Int,
    val byteCount: Int,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate
)

class EpaperPayloadException(message: String) : IllegalArgumentException(message)

object EpaperPayloadBuilder {
    const val DEFAULT_QUOTE = "Cada dia cuenta"

    fun build(
        events: List<CalendarEvent>,
        selectedDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
        calendarName: String = "Mi calendario",
        quote: String = DEFAULT_QUOTE
    ): EpaperPayloadResult {
        val selectedMonth = YearMonth.from(selectedDate)
        val rangeStart = selectedMonth.minusMonths(6).atDay(1)
        val rangeEnd = selectedMonth.plusMonths(6).atEndOfMonth()
        val occurrences = mutableListOf<CalendarOccurrence>()
        events.filterNot { it.category == EventCategory.TASK && it.done }.forEach { event ->
            occurrences += event.epaperOccurrences(rangeStart, rangeEnd)
            if (occurrences.size > EPAPER_MAX_OCCURRENCES) {
                throw EpaperPayloadException(
                    "Hay más de $EPAPER_MAX_OCCURRENCES apariciones; reduce el intervalo o las recurrencias."
                )
            }
        }
        occurrences.sortWith(
            compareBy<CalendarOccurrence> { it.startDate }
                .thenBy { it.event.startMinutes }
                .thenBy { it.event.title }
        )

        val json = buildString {
            append('{')
            field("version", "2", quoted = false)
            field("generatedAt", now.toString())
            field("timezone", zoneId.id)
            field("today", LocalDate.now(zoneId).toString())
            field("selectedDate", selectedDate.toString())
            field("rangeStart", rangeStart.toString())
            field("rangeEnd", rangeEnd.toString())
            field("calendarName", calendarName.toEpaperAscii())
            field("quote", quote.toEpaperAscii())
            append(",\"events\":[")
            occurrences.forEachIndexed { index, occurrence ->
                if (index > 0) append(',')
                val event = occurrence.event
                append('{')
                field("occurrenceId", "${event.id}@${occurrence.startDate}")
                field("date", occurrence.startDate.toString())
                field("endDate", occurrence.endDate.toString())
                field("time", event.startLabel)
                field("end", event.endLabel)
                field("title", event.title.toEpaperAscii())
                field("category", event.category.name)
                field("allDay", event.allDay.toString(), quoted = false)
                field("done", event.done.toString(), quoted = false, leadingComma = true)
                append('}')
            }
            append("]}")
        }
        val bytes = json.toByteArray(StandardCharsets.UTF_8).size
        if (bytes > EPAPER_MAX_PAYLOAD_BYTES) {
            throw EpaperPayloadException(
                "El calendario ocupa $bytes bytes; el dispositivo admite como máximo $EPAPER_MAX_PAYLOAD_BYTES."
            )
        }
        return EpaperPayloadResult(json, occurrences.size, bytes, rangeStart, rangeEnd)
    }

    private fun StringBuilder.field(
        name: String,
        value: String,
        quoted: Boolean = true,
        leadingComma: Boolean = isNotEmpty() && last() != '{'
    ) {
        if (leadingComma) append(',')
        append('"').append(name).append("\":")
        if (quoted) append('"').append(escapeJson(value)).append('"') else append(value)
    }
}

private fun CalendarEvent.epaperOccurrences(rangeStart: LocalDate, rangeEnd: LocalDate): List<CalendarOccurrence> {
    if (recurrenceRule == RecurrenceRule.CUSTOM && customWeekDays.isNotEmpty()) {
        return customWeekdayOccurrences(rangeStart, rangeEnd, EPAPER_MAX_OCCURRENCES + 1)
    }
    if (recurrenceRule == RecurrenceRule.NONE) {
        return occurrencesOverlapping(rangeStart, rangeEnd)
    }
    val durationDays = ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0)
    if (recurrenceRule == RecurrenceRule.MONTHLY) {
        var cursor = date
        while (cursor.plusDays(durationDays).isBefore(rangeStart)) {
            if (recurrenceEndDate != null && cursor.isAfter(recurrenceEndDate)) return emptyList()
            cursor = cursor.plusMonths(1)
        }
        val results = mutableListOf<CalendarOccurrence>()
        while (!cursor.isAfter(rangeEnd)) {
            if (recurrenceEndDate != null && cursor.isAfter(recurrenceEndDate)) break
            val occurrence = CalendarOccurrence(this, cursor, cursor.plusDays(durationDays))
            if (occurrence.overlaps(rangeStart, rangeEnd) && cursor !in excludedDates) results += occurrence
            cursor = cursor.plusMonths(1)
        }
        return results
    }
    val stepDays = when (recurrenceRule) {
        RecurrenceRule.DAILY -> 1L
        RecurrenceRule.WEEKLY -> 7L
        RecurrenceRule.CUSTOM -> customRepeatDays.coerceAtLeast(1).toLong()
        RecurrenceRule.NONE,
        RecurrenceRule.MONTHLY -> error("Handled above")
    }
    val earliestUsefulStart = rangeStart.minusDays(durationDays)
    val distance = ChronoUnit.DAYS.between(date, earliestUsefulStart).coerceAtLeast(0)
    var cursor = date.plusDays((distance / stepDays) * stepDays)
    while (cursor.isBefore(earliestUsefulStart)) cursor = cursor.plusDays(stepDays)

    val results = mutableListOf<CalendarOccurrence>()
    while (!cursor.isAfter(rangeEnd)) {
        if (recurrenceEndDate != null && cursor.isAfter(recurrenceEndDate)) break
        val occurrence = CalendarOccurrence(this, cursor, cursor.plusDays(durationDays))
        if (occurrence.overlaps(rangeStart, rangeEnd) && cursor !in excludedDates) results += occurrence
        cursor = cursor.plusDays(stepDays)
    }
    return results
}

internal fun escapeJson(value: String): String = buildString(value.length + 8) {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
}

private fun String.toEpaperAscii(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .map { if (it.code in 32..126) it else '?' }
    .joinToString("")

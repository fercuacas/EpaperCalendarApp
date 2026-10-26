package com.fernando.epapercalendar.calendar

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.text.Normalizer
import java.util.Locale
import com.fernando.epapercalendar.calendar.device.DeviceEventSource

data class CalendarEvent(
    val id: String,
    val title: String,
    val location: String,
    val date: LocalDate,
    val endDate: LocalDate = date,
    val startMinutes: Int,
    val endMinutes: Int,
    val category: EventCategory,
    val people: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val customRepeatDays: Int = 7,
    val recurrenceEndDate: LocalDate? = null,
    val excludedDates: List<LocalDate> = emptyList(),
    val reminder: ReminderOption = ReminderOption.NONE,
    val done: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceSource: DeviceEventSource? = null,
    val customWeekDays: Set<DayOfWeek> = emptySet()
) {
    val isReadOnly: Boolean get() = deviceSource != null
    val startLabel: String = minutesToLabel(startMinutes)
    val endLabel: String = minutesToLabel(endMinutes)
    val spansMultipleDays: Boolean = endDate.isAfter(date)
    val allDayLike: Boolean = allDay || spansMultipleDays
    val durationDays: Long = ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0) + 1

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.normalizedForSearch()
        return title.normalizedForSearch().contains(needle) ||
            location.normalizedForSearch().contains(needle) ||
            people.normalizedForSearch().contains(needle) ||
            notes.normalizedForSearch().contains(needle) ||
            category.label.normalizedForSearch().contains(needle) ||
            category.shortLabel.normalizedForSearch().contains(needle)
    }

    fun occurrencesOverlapping(rangeStart: LocalDate, rangeEnd: LocalDate): List<CalendarOccurrence> {
        if (rangeEnd.isBefore(rangeStart)) return emptyList()
        val occurrenceLength = ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0)

        fun occurrence(start: LocalDate): CalendarOccurrence {
            return CalendarOccurrence(
                event = this,
                startDate = start,
                endDate = start.plusDays(occurrenceLength)
            )
        }

        if (recurrenceRule == RecurrenceRule.NONE) {
            val single = occurrence(date)
            return if (single.overlaps(rangeStart, rangeEnd)) listOf(single) else emptyList()
        }

        if (recurrenceRule == RecurrenceRule.CUSTOM && customWeekDays.isNotEmpty()) {
            return customWeekdayOccurrences(rangeStart, rangeEnd)
        }

        val results = mutableListOf<CalendarOccurrence>()
        var cursor = date
        var guard = 0
        while (!cursor.minusDays(occurrenceLength).isAfter(rangeEnd) && guard < 520) {
            if (recurrenceEndDate != null && cursor.isAfter(recurrenceEndDate)) break
            val item = occurrence(cursor)
            if (item.overlaps(rangeStart, rangeEnd) && cursor !in excludedDates) {
                results.add(item)
            }
            cursor = when (recurrenceRule) {
                RecurrenceRule.NONE -> rangeEnd.plusDays(1)
                RecurrenceRule.DAILY -> cursor.plusDays(1)
                RecurrenceRule.WEEKLY -> cursor.plusWeeks(1)
                RecurrenceRule.MONTHLY -> cursor.plusMonths(1)
                RecurrenceRule.CUSTOM -> cursor.plusDays(customRepeatDays.coerceAtLeast(1).toLong())
            }
            guard++
        }
        return results
    }

    companion object {
        fun minutesToLabel(minutes: Int): String {
            val hour = (minutes / 60).coerceIn(0, 23)
            val minute = (minutes % 60).coerceIn(0, 59)
            return "%02d:%02d".format(hour, minute)
        }

        fun labelToMinutes(label: String): Int? {
            val parts = label.trim().split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }
    }
}

/** Shared with e-paper; payload requests one beyond its cap so overflow is rejected, never hidden. */
internal fun CalendarEvent.customWeekdayOccurrences(
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    limit: Int = 520
): List<CalendarOccurrence> {
    if (rangeEnd.isBefore(rangeStart) || customWeekDays.isEmpty()) return emptyList()
    val duration = ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0)
    val earliest = maxOf(date, rangeStart.minusDays(duration))
    var cursor = earliest.plusDays((0L..6L).first { earliest.plusDays(it).dayOfWeek in customWeekDays })
    val results = mutableListOf<CalendarOccurrence>()
    // Skip old history directly, retaining inclusive ends and stable occurrence start dates.
    while (!cursor.isAfter(rangeEnd) && results.size < limit) {
        if (recurrenceEndDate != null && cursor.isAfter(recurrenceEndDate)) break
        val occurrence = CalendarOccurrence(this, cursor, cursor.plusDays(duration))
        if (occurrence.overlaps(rangeStart, rangeEnd) && cursor !in excludedDates) results.add(occurrence)
        cursor = cursor.plusDays((1L..7L).first { cursor.plusDays(it).dayOfWeek in customWeekDays })
    }
    return results
}

data class CalendarOccurrence(
    val event: CalendarEvent,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    val isAllDayLike: Boolean = event.allDay || endDate.isAfter(startDate)

    fun includes(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)

    fun overlaps(rangeStart: LocalDate, rangeEnd: LocalDate): Boolean {
        return !endDate.isBefore(rangeStart) && !startDate.isAfter(rangeEnd)
    }

    fun asCalendarEvent(): CalendarEvent = event.copy(
        date = startDate,
        endDate = endDate
    )
}

enum class EventCategory(
    val label: String,
    val shortLabel: String,
    val accentColor: Long,
    val softColor: Long
) {
    WORK("Trabajo", "Work", 0xFFFF6042, 0xFFFFE2D7),
    FOCUS("Focus time", "Focus", 0xFFFF8A35, 0xFFFFEAD6),
    COFFEE("Café", "Café", 0xFF9B5837, 0xFFFFE9D9),
    FITNESS("Fitness", "Fit", 0xFFB47DFF, 0xFFF0E5FF),
    PERSONAL("Personal", "Casa", 0xFF4BAA7D, 0xFFDDF5E9),
    TASK("Tarea", "Task", 0xFF6E88F7, 0xFFE2E8FF)
}

enum class RecurrenceRule(val label: String) {
    NONE("No se repite"),
    DAILY("Cada día"),
    WEEKLY("Cada semana"),
    MONTHLY("Cada mes"),
    CUSTOM("Personalizado")
}

enum class ReminderOption(val label: String, val minutesBefore: Int?) {
    NONE("Sin aviso", null),
    FIVE_MINUTES("5 min antes", 5),
    THIRTY_MINUTES("30 min antes", 30),
    DAY_BEFORE("1 día antes", 24 * 60)
}

enum class RecurrenceEditMode {
    SINGLE_OCCURRENCE,
    THIS_AND_FUTURE,
    ENTIRE_SERIES
}

enum class RecurrenceDeleteMode {
    SINGLE_OCCURRENCE,
    ENTIRE_SERIES
}

data class EventDraft(
    val id: String? = null,
    val title: String = "",
    val location: String = "",
    val date: LocalDate = LocalDate.now(),
    val endDate: LocalDate = date,
    val startLabel: String = "09:00",
    val endLabel: String = "10:00",
    val category: EventCategory = EventCategory.WORK,
    val people: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val customRepeatDays: Int = 7,
    val recurrenceEndDate: LocalDate? = null,
    val excludedDates: List<LocalDate> = emptyList(),
    val reminder: ReminderOption = ReminderOption.NONE,
    val done: Boolean = false,
    val customWeekDays: Set<DayOfWeek> = emptySet()
)

fun CalendarEvent.toDraft(): EventDraft = EventDraft(
    id = id,
    title = title,
    location = location,
    date = date,
    endDate = endDate,
    startLabel = startLabel,
    endLabel = endLabel,
    category = category,
    people = people,
    notes = notes,
    allDay = allDay,
    recurrenceRule = recurrenceRule,
    customRepeatDays = customRepeatDays,
    customWeekDays = customWeekDays,
    recurrenceEndDate = recurrenceEndDate,
    excludedDates = excludedDates,
    reminder = reminder,
    done = done
)

private fun String.normalizedForSearch(): String {
    val lower = trim().lowercase(Locale.getDefault())
    return Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
}

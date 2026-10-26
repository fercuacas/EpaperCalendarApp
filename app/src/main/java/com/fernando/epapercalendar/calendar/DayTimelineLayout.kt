package com.fernando.epapercalendar.calendar

internal data class DayTimelinePlacement(
    val event: CalendarEvent,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
    val columnSpan: Int
)

/** Endpoints are exclusive: 09:00–10:00 and 10:00–11:00 do not overlap. */
internal fun layoutDayTimeline(
    events: List<CalendarEvent>,
    minimumVisualMinutes: Int = 15
): List<DayTimelinePlacement> {
    data class Slot(val event: CalendarEvent, val start: Int, val end: Int, var column: Int = 0)
    val slots = events.filterNot { it.allDayLike }.map { event ->
        val start = event.startMinutes.coerceIn(0, 1439)
        // Short events retain a small, readable hit area; include it in collision detection.
        Slot(event, start, maxOf(event.endMinutes, start + minimumVisualMinutes.coerceAtLeast(1)).coerceAtMost(1440))
    }.sortedWith(compareBy<Slot> { it.start }.thenByDescending { it.end }.thenBy { it.event.id })
    val result = mutableListOf<DayTimelinePlacement>()
    var index = 0
    while (index < slots.size) {
        val group = mutableListOf(slots[index++])
        var groupEnd = group.first().end
        while (index < slots.size && slots[index].start < groupEnd) {
            val next = slots[index++]
            group += next
            groupEnd = maxOf(groupEnd, next.end)
        }
        val columnEnds = mutableListOf<Int>()
        group.forEach { slot ->
            val available = columnEnds.indexOfFirst { it <= slot.start }
            slot.column = if (available < 0) columnEnds.size else available
            if (available < 0) columnEnds += slot.end else columnEnds[available] = slot.end
        }
        group.forEach { slot ->
            var span = 1
            for (column in slot.column + 1 until columnEnds.size) {
                if (group.any { it.column == column && it.start < slot.end && slot.start < it.end }) break
                span++
            }
            result += DayTimelinePlacement(slot.event, slot.start, slot.end, slot.column, columnEnds.size, span)
        }
    }
    return result
}

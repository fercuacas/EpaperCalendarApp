package com.fernando.epapercalendar.calendar

internal data class WeekTimelinePlacement(
    val events: List<CalendarEvent>,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
    val columnSpan: Int
)

/** Group exact starts; retain every event's end for the individual duration strips. */
internal fun layoutWeekTimeline(events: List<CalendarEvent>): List<WeekTimelinePlacement> {
    val groups = events.filterNot { it.allDayLike }
        .groupBy { it.startMinutes.coerceIn(0, 1439) }
        .mapValues { (_, group) ->
            group.sortedWith(compareByDescending<CalendarEvent> { it.endMinutes }.thenBy { it.id })
        }
    val representatives = groups.map { (start, group) ->
        group.first().copy(startMinutes = start, endMinutes = group.maxOf { it.endMinutes })
    }
    // The weekly grid uses actual durations, including short events and adjacent ends.
    return layoutDayTimeline(representatives, minimumVisualMinutes = 1).map { placement ->
        WeekTimelinePlacement(
            events = groups.getValue(placement.startMinute),
            startMinute = placement.startMinute,
            endMinute = placement.endMinute,
            column = placement.column,
            columnCount = placement.columnCount,
            columnSpan = placement.columnSpan
        )
    }
}

package com.fernando.epapercalendar

import com.fernando.epapercalendar.calendar.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DayTimelineLayoutTest {
    private fun event(id: String, start: Int, end: Int) = CalendarEvent(
        id, id, "", LocalDate.of(2026, 10, 23), startMinutes = start, endMinutes = end,
        category = EventCategory.WORK
    )

    @Test fun isolatedAndAdjacentEventsUseFullWidth() {
        val placements = layoutDayTimeline(listOf(event("a", 480, 540), event("b", 540, 600), event("c", 720, 780)))
        assertEquals(3, placements.size)
        assertTrue(placements.all { it.column == 0 && it.columnCount == 1 && it.columnSpan == 1 })
    }

    @Test fun simultaneousEventsAreParallelAndKeepTheirDurations() {
        val placements = layoutDayTimeline(listOf(event("a", 540, 660), event("b", 540, 600), event("c", 570, 630)))
        assertEquals(setOf(0, 1, 2), placements.map { it.column }.toSet())
        assertTrue(placements.all { it.columnCount == 3 })
        assertEquals(120, placements.first { it.event.id == "a" }.let { it.endMinute - it.startMinute })
    }

    @Test fun chainedOverlapsReuseAvailableColumns() {
        val placements = layoutDayTimeline(listOf(event("a", 540, 600), event("b", 570, 660), event("c", 600, 630)))
        assertTrue(placements.all { it.columnCount == 2 })
        assertEquals(placements[0].column, placements.first { it.event.id == "c" }.column)
    }

    @Test fun laterIsolatedEventDoesNotInheritEarlierColumnCount() {
        val placements = layoutDayTimeline(listOf(event("a", 480, 600), event("b", 510, 570), event("c", 660, 720)))
        assertEquals(1, placements.last().columnCount)
    }

    @Test fun shortEventsAndMidnightRemainWithinThe24HourGrid() {
        val placements = layoutDayTimeline(listOf(event("early", 0, 5), event("late", 1438, 1439)))
        assertEquals(0, placements.first().startMinute)
        assertEquals(15, placements.first().endMinute)
        assertEquals(1440, placements.last().endMinute)
    }

    @Test fun allDayAndMultidayEventsAreKeptOutsideTheHourlyGrid() {
        val base = event("base", 540, 600)
        assertTrue(layoutDayTimeline(listOf(base.copy(allDay = true), base.copy(endDate = base.date.plusDays(1)))).isEmpty())
        assertTrue(layoutDayTimeline(emptyList()).isEmpty())
    }

    @Test fun layoutIsStableAndNeverOverlapsRectanglesForConcurrentEvents() {
        val events = listOf(event("a", 480, 780), event("b", 480, 540), event("c", 480, 540),
            event("d", 540, 600), event("e", 610, 660), event("f", 780, 840))
        val placements = layoutDayTimeline(events)
        assertEquals(placements, layoutDayTimeline(events.reversed()))
        placements.forEach { a ->
            placements.filter { it !== a }.forEach { b ->
                if (a.startMinute < b.endMinute && b.startMinute < a.endMinute) {
                    assertTrue(a.column + a.columnSpan <= b.column || b.column + b.columnSpan <= a.column)
                }
            }
        }
        assertEquals(2, placements.first { it.event.id == "d" }.columnSpan)
    }
}

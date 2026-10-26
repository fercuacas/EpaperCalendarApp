package com.fernando.epapercalendar

import com.fernando.epapercalendar.calendar.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekTimelineLayoutTest {
    private val date = LocalDate.of(2026, 10, 23)
    private fun event(id: String, start: Int, end: Int) = CalendarEvent(
        id, id, "", date, startMinutes = start, endMinutes = end, category = EventCategory.WORK)

    @Test fun identicalStartsShareACounterButKeepEveryFinish() {
        val events = listOf(event("short", 540, 600), event("long", 540, 780), event("third", 540, 660))
        val placement = layoutWeekTimeline(events).single()
        assertEquals(3, placement.events.size)
        assertEquals(setOf(600, 660, 780), placement.events.map { it.endMinutes }.toSet())
        assertEquals(540, placement.startMinute)
        assertEquals(780, placement.endMinute)
        assertEquals(1, placement.columnCount)
    }

    @Test fun differentStartsWithinTheSameHourStaySeparateAndParallel() {
        val result = layoutWeekTimeline(listOf(event("long", 540, 780), event("later", 570, 630)))
        assertEquals(2, result.size)
        assertTrue(result.all { it.columnCount == 2 })
        assertNotEquals(result.first().column, result.last().column)
        assertEquals(570, result.last().startMinute)
        assertEquals(630, result.last().endMinute)
    }

    @Test fun shortAdjacentEventsKeepActualDurationsWithoutFalseOverlaps() {
        val result = layoutWeekTimeline(listOf(event("a", 540, 545), event("b", 545, 550)))
        assertEquals(listOf(545, 550), result.map { it.endMinute })
        assertTrue(result.all { it.columnCount == 1 })
    }

    @Test fun aSameStartGroupBlocksItsLaneUntilItsLongestEventEnds() {
        val result = layoutWeekTimeline(listOf(event("a", 540, 600), event("b", 540, 720),
            event("c", 600, 660), event("d", 660, 720), event("after", 720, 780)))
        val group = result.first()
        val c = result.first { it.startMinute == 600 }
        val d = result.first { it.startMinute == 660 }
        assertNotEquals(group.column, c.column)
        assertEquals(c.column, d.column)
        assertEquals(1, result.last().columnCount)
    }

    @Test fun orderingIsStableAndGroupsNeverHideEachOther() {
        val events = listOf(event("a", 540, 780), event("b", 540, 600),
            event("c", 560, 620), event("d", 580, 700), event("e", 650, 750))
        val result = layoutWeekTimeline(events)
        assertEquals(result, layoutWeekTimeline(events.reversed()))
        result.forEach { a ->
            result.filter { it != a }.forEach { b ->
                if (a.startMinute < b.endMinute && b.startMinute < a.endMinute) {
                    assertTrue(a.column + a.columnSpan <= b.column || b.column + b.columnSpan <= a.column)
                }
            }
        }
        assertEquals(events.size, result.sumOf { it.events.size })
    }

    @Test fun allDayAndMultidayStaySeparateAndDayBoundariesArePreserved() {
        val base = event("base", 0, 1)
        val result = layoutWeekTimeline(listOf(base, base.copy(id = "all", allDay = true),
            base.copy(id = "span", endDate = date.plusDays(1)), event("late", 1438, 1439)))
        assertEquals(2, result.size)
        assertEquals(1, result.first().endMinute)
        assertEquals(1439, result.last().endMinute)
        assertTrue(layoutWeekTimeline(emptyList()).isEmpty())
    }
}

package com.fernando.epapercalendar

import com.fernando.epapercalendar.calendar.*
import com.fernando.epapercalendar.calendar.device.DeviceCalendarIdentity
import com.fernando.epapercalendar.calendar.device.DeviceEventSource
import com.fernando.epapercalendar.epaper.EpaperPayloadBuilder
import com.fernando.epapercalendar.epaper.EpaperPayloadException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceBehaviorTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 31)
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    private fun event() = CalendarEvent("series", "Clase", "", start, startMinutes = 540, endMinutes = 600,
        category = EventCategory.WORK, recurrenceRule = RecurrenceRule.CUSTOM, customWeekDays = weekdays,
        reminder = ReminderOption.FIVE_MINUTES)

    @Test fun customWeekdaysOnlyProduceSelectedDaysAndNoExtraInitialDay() {
        val expected = (0L..30L).map(start::plusDays).filter { it.dayOfWeek in weekdays }
        assertEquals(expected, event().occurrencesOverlapping(start, end).map { it.startDate })
        assertFalse(event().occurrencesOverlapping(start, end).any { it.startDate == start })
    }

    @Test fun oldSeriesJumpsToCurrentRangeWithoutScanningAllHistory() {
        val ancient = event().copy(date = start.minusYears(15), endDate = start.minusYears(15))
        assertEquals(event().occurrencesOverlapping(start, end).map { it.startDate },
            ancient.occurrencesOverlapping(start, end).map { it.startDate })
    }

    @Test fun inclusiveEndAndExclusionsDoNotShiftWeekdayPattern() {
        val last = LocalDate.of(2026, 10, 23)
        val skipped = LocalDate.of(2026, 10, 5)
        val items = event().copy(recurrenceEndDate = last, excludedDates = listOf(skipped)).occurrencesOverlapping(start, end)
        assertFalse(items.any { it.startDate == skipped })
        assertEquals(last, items.last().startDate)
        assertTrue(items.all { it.startDate.dayOfWeek in weekdays })
    }

    @Test fun legacyEveryNDaysRemainsUnchangedWithEmptyWeekdaySet() {
        val legacy = event().copy(customWeekDays = emptySet(), customRepeatDays = 3)
        assertEquals(listOf(start, start.plusDays(3), start.plusDays(6), start.plusDays(9)),
            legacy.occurrencesOverlapping(start, start.plusDays(10)).map { it.startDate })
    }

    @Test fun multiDayOccurrenceUsesItsOwnStartEvenWhenOpenedOnAnotherDay() {
        val monday = LocalDate.of(2026, 10, 5)
        val spanning = event().copy(date = monday, endDate = monday.plusDays(2), customWeekDays = setOf(DayOfWeek.MONDAY))
        val state = CalendarState(MemoryCalendarStore(listOf(spanning)))
        val clicked = state.eventsFor(monday.plusDays(1)).single().toDraft()
        assertEquals(monday, clicked.date)
        assertEquals(spanning.customWeekDays, clicked.customWeekDays)
        assertTrue(state.deleteEvent(clicked.id!!, clicked.date, RecurrenceDeleteMode.SINGLE_OCCURRENCE))
        assertTrue(state.eventsFor(monday.plusDays(1)).isEmpty())
        assertEquals(1, state.eventsFor(monday.plusWeeks(1)).size)
    }

    @Test fun deleteOneDayPersistsExceptionAndRemovesItFromPayloadOnly() {
        val store = MemoryCalendarStore(listOf(event()))
        val state = CalendarState(store)
        val skipped = LocalDate.of(2026, 10, 5)
        assertTrue(state.deleteEvent("series", skipped, RecurrenceDeleteMode.SINGLE_OCCURRENCE))
        assertEquals(listOf(skipped), store.events.single().excludedDates)
        val restored = CalendarState(store)
        assertTrue(restored.eventsFor(skipped).isEmpty())
        assertEquals(1, restored.eventsFor(skipped.plusDays(2)).size)
        val payload = EpaperPayloadBuilder.build(restored.events, skipped)
        val events = JSONObject(payload.json).getJSONArray("events")
        val dates = (0 until events.length()).map { events.getJSONObject(it).getString("date") }
        assertFalse(dates.contains(skipped.toString()))
        assertTrue(dates.contains(skipped.plusDays(2).toString()))
        assertFalse(state.deleteEvent("series", skipped, RecurrenceDeleteMode.SINGLE_OCCURRENCE))
    }

    @Test fun deleteWholeSeriesKeepsIndependentEventsAndImportedEventsSafe() {
        val independent = event().copy(id = "other", recurrenceRule = RecurrenceRule.NONE, customWeekDays = emptySet())
        val store = MemoryCalendarStore(listOf(event(), independent))
        val state = CalendarState(store)
        assertTrue(state.deleteEvent("series", LocalDate.of(2026, 10, 23), RecurrenceDeleteMode.ENTIRE_SERIES))
        assertEquals(listOf(independent), store.events)
        val imported = independent.copy(id = "device:1:1:0", deviceSource = DeviceEventSource(
            DeviceCalendarIdentity(1, "test", "test"), "Original", 1, 0))
        state.replaceImportedEvents(listOf(imported))
        assertFalse(state.deleteEvent(imported.id, start, RecurrenceDeleteMode.SINGLE_OCCURRENCE))
        assertFalse(state.deleteEvent(imported.id, start, RecurrenceDeleteMode.ENTIRE_SERIES))
        assertEquals(2, state.events.size)
    }

    @Test fun invalidSingleDeletionDoesNotChangeStoredSeries() {
        val store = MemoryCalendarStore(listOf(event()))
        val state = CalendarState(store)
        val before = store.events
        assertFalse(state.deleteEvent("series", null, RecurrenceDeleteMode.SINGLE_OCCURRENCE))
        assertFalse(state.deleteEvent("series", start, RecurrenceDeleteMode.SINGLE_OCCURRENCE))
        assertFalse(state.deleteEvent("missing", start, RecurrenceDeleteMode.ENTIRE_SERIES))
        assertEquals(before, store.events)
    }

    @Test fun editingAndSplittingSeriesPreserveWeekdaysAndSingleEditDoesNotRepeat() {
        val occurrenceDate = LocalDate.of(2026, 10, 23)
        RecurrenceEditMode.entries.forEach { mode ->
            val store = MemoryCalendarStore(listOf(event()))
            val state = CalendarState(store)
            val draft = state.eventsFor(occurrenceDate).single().toDraft().copy(title = "Cambio")
            assertTrue(state.saveRecurringEdit(draft, mode))
            val changed = state.events.single { it.title == "Cambio" }
            if (mode == RecurrenceEditMode.SINGLE_OCCURRENCE) {
                assertEquals(RecurrenceRule.NONE, changed.recurrenceRule)
                assertTrue(changed.customWeekDays.isEmpty())
                assertTrue(occurrenceDate in state.events.single { it.id == "series" }.excludedDates)
            } else assertEquals(weekdays, changed.customWeekDays)
        }
    }

    @Test fun remindersSkipDeletedDayAndRemainCorrectAcrossDst() {
        val zone = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.parse("2026-10-22T12:00:00+02:00[Europe/Madrid]").toInstant().toEpochMilli()
        val before = ZonedDateTime.parse("2026-10-23T08:55:00+02:00[Europe/Madrid]").toInstant().toEpochMilli()
        val after = ZonedDateTime.parse("2026-10-26T08:55:00+01:00[Europe/Madrid]").toInstant().toEpochMilli()
        assertEquals(before, nextReminderTriggerMillis(event(), 5, now, zone))
        val state = CalendarState(MemoryCalendarStore(listOf(event())))
        assertTrue(state.deleteEvent("series", LocalDate.of(2026, 10, 23), RecurrenceDeleteMode.SINGLE_OCCURRENCE))
        assertEquals(after, nextReminderTriggerMillis(state.events.single(), 5, now, zone))
    }

    @Test fun largeOverlappingWeekdaySeriesIsRejectedInsteadOfTruncatedForEpaper() {
        val long = event().copy(date = start.minusYears(2), endDate = start.plusYears(1),
            customWeekDays = DayOfWeek.values().toSet())
        assertThrows(EpaperPayloadException::class.java) { EpaperPayloadBuilder.build(listOf(long), start) }
    }
}

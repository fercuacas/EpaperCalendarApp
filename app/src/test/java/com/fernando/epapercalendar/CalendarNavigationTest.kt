package com.fernando.epapercalendar

import com.fernando.epapercalendar.calendar.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarNavigationTest {
    private val date = LocalDate.of(2026,10,31)
    private val events = listOf(
        CalendarEvent("work", "Trabajo", "", date, startMinutes = 600, endMinutes = 660, category = EventCategory.WORK),
        CalendarEvent("personal", "Personal", "", date, startMinutes = 600, endMinutes = 660, category = EventCategory.PERSONAL),
        CalendarEvent("task", "Tarea", "", date, startMinutes = 600, endMinutes = 660, category = EventCategory.TASK)
    )
    private fun state() = CalendarState(object : CalendarStore {
        override fun loadEvents() = events
        override fun saveEvents(events: List<CalendarEvent>) = Unit
        override fun loadSection() = AppSection.AGENDA
        override fun saveSection(section: AppSection) = Unit
        override fun loadCategoryFilter(): EventCategory? = null
        override fun saveCategoryFilter(category: EventCategory?) = Unit
        override fun loadDateFilter() = DateFilter.ALL
        override fun saveDateFilter(filter: DateFilter) = Unit
    }).apply { selectDate(date) }

    @Test fun filtersAndSearchOnlyApplyToWeekAndMonthAndAreRetainedWhenReturning() {
        val state = state()
        state.updateCategoryFilter(EventCategory.WORK)
        state.searchQuery = "Trabajo"
        for (section in AppSection.values()) {
            state.selectSection(section)
            assertEquals(section == AppSection.WEEK || section == AppSection.CALENDAR, state.filtersEnabled)
            assertEquals(if (state.filtersEnabled) 1 else 3, state.eventsFor(date).size)
            assertEquals(if (state.filtersEnabled) 0 else 1, state.taskEvents().size)
        }
        state.selectSection(AppSection.AGENDA)
        state.updateDateFilter(DateFilter.TODAY)
        assertEquals(3, state.eventCountFor(date))
        state.selectSection(AppSection.TASKS)
        assertEquals("task", state.taskEvents().single().id)
        state.selectSection(AppSection.WEEK)
        assertEquals(EventCategory.WORK, state.categoryFilter)
        assertEquals("Trabajo", state.searchQuery)
        assertEquals(DateFilter.TODAY, state.dateFilter)
        state.clearFilters()
        assertEquals(3, state.eventsFor(date).size)
    }

    @Test fun weekNavigationPreservesWeekdayAcrossYearsAndUpdatesVisibleMonth() {
        val state = state()
        val selected = LocalDate.of(2026,12,30)
        state.selectDate(selected)
        state.navigateWeek(1)
        assertEquals(LocalDate.of(2027,1,6), state.selectedDate)
        assertEquals(selected.dayOfWeek, state.selectedDate.dayOfWeek)
        assertEquals(YearMonth.of(2027,1), state.visibleMonth)
        state.navigateWeek(-1)
        assertEquals(selected, state.selectedDate)
    }

    @Test fun monthNavigationClampsEndOfMonthAndHandlesLeapYearsAndYearBoundaries() {
        val state = state()
        state.selectDate(LocalDate.of(2027,1,31))
        state.navigateMonth(1)
        assertEquals(LocalDate.of(2027,2,28), state.selectedDate)
        state.selectDate(LocalDate.of(2028,1,31))
        state.navigateMonth(1)
        assertEquals(LocalDate.of(2028,2,29), state.selectedDate)
        state.selectDate(LocalDate.of(2027,1,15))
        state.navigateMonth(-1)
        assertEquals(LocalDate.of(2026,12,15), state.selectedDate)
        assertEquals(YearMonth.of(2026,12), state.visibleMonth)
        state.navigateMonth(1)
        assertEquals(LocalDate.of(2027,1,15), state.selectedDate)
    }
}

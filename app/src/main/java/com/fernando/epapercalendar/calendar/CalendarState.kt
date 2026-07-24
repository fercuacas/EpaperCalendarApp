package com.fernando.epapercalendar.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class CalendarState(
    private val repository: CalendarRepository,
    private val reminderScheduler: CalendarReminderScheduler? = null,
    initialEvents: List<CalendarEvent>? = null,
    private val persistChanges: Boolean = true
) {
    private val eventItems = mutableStateListOf<CalendarEvent>()

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var visibleMonth by mutableStateOf(YearMonth.from(selectedDate))
        private set

    var section by mutableStateOf(if (persistChanges) repository.loadSection() else AppSection.AGENDA)
        private set

    var searchQuery by mutableStateOf("")

    var categoryFilter by mutableStateOf(if (persistChanges) repository.loadCategoryFilter() else null)
        private set

    var dateFilter by mutableStateOf(if (persistChanges) repository.loadDateFilter() else DateFilter.ALL)
        private set

    var syncStatus by mutableStateOf("Listo para preparar el envío")
        private set

    val events: List<CalendarEvent>
        get() = eventItems

    init {
        eventItems.addAll(initialEvents ?: repository.loadEvents())
        if (persistChanges) {
            reminderScheduler?.scheduleAll(eventItems)
            persist()
        }
    }

    fun selectSection(section: AppSection) {
        this.section = section
        if (persistChanges) repository.saveSection(section)
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        visibleMonth = YearMonth.from(date)
    }

    fun updateCategoryFilter(category: EventCategory?) {
        categoryFilter = category
        if (persistChanges) repository.saveCategoryFilter(category)
    }

    fun updateDateFilter(filter: DateFilter) {
        dateFilter = filter
        if (persistChanges) repository.saveDateFilter(filter)
    }

    fun clearFilters() {
        categoryFilter = null
        dateFilter = DateFilter.ALL
        searchQuery = ""
        if (persistChanges) {
            repository.saveCategoryFilter(null)
            repository.saveDateFilter(DateFilter.ALL)
        }
    }

    fun eventsFor(date: LocalDate): List<CalendarEvent> = occurrencesInRange(date, date)
        .map { it.asCalendarEvent() }
        .distinctBy { it.id }
        .sortedBy { it.startMinutes }

    fun timedEventsFor(date: LocalDate): List<CalendarEvent> = occurrencesInRange(date, date)
        .filter { !it.isAllDayLike }
        .map { it.asCalendarEvent() }
        .distinctBy { it.id }
        .sortedBy { it.startMinutes }

    fun allDayEventsFor(date: LocalDate): List<CalendarEvent> = occurrencesInRange(date, date)
        .filter { it.isAllDayLike }
        .map { it.asCalendarEvent() }
        .distinctBy { it.id }
        .sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.title })

    fun eventCountFor(date: LocalDate): Int = occurrencesInRange(date, date).distinctBy { it.event.id }.size

    fun multiDayCountFor(date: LocalDate): Int = occurrencesInRange(date, date)
        .filter { it.isAllDayLike }
        .distinctBy { it.event.id }
        .size

    fun upcomingEvents(limit: Int = 8): List<CalendarEvent> = eventItems
        .flatMap { it.occurrencesOverlapping(selectedDate, selectedDate.plusMonths(6)) }
        .filter { it.matchesFilters() }
        .sortedWith(compareBy<CalendarOccurrence> { it.startDate }.thenBy { it.event.startMinutes })
        .map { it.asCalendarEvent() }
        .distinctBy { it.id }
        .take(limit)

    fun monthEvents(): List<CalendarEvent> = eventItems
        .filter { event ->
            event.occurrencesOverlapping(visibleMonth.atDay(1), visibleMonth.atEndOfMonth())
                .any { it.matchesFilters() }
        }
        .sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.startMinutes })

    fun taskEvents(): List<CalendarEvent> = eventItems
        .filter { it.category == EventCategory.TASK && it.matchesFilters() }
        .sortedWith(compareBy<CalendarEvent> { it.done }.thenBy { it.date }.thenBy { it.startMinutes })

    fun occurrencesInRange(start: LocalDate, end: LocalDate): List<CalendarOccurrence> {
        return eventItems
            .flatMap { it.occurrencesOverlapping(start, end) }
            .filter { it.matchesFilters() }
            .sortedWith(compareBy<CalendarOccurrence> { it.startDate }.thenBy { it.event.startMinutes })
    }

    fun spanOccurrencesInRange(start: LocalDate, end: LocalDate): List<CalendarOccurrence> {
        return occurrencesInRange(start, end)
            .filter { it.isAllDayLike }
            .sortedWith(compareBy<CalendarOccurrence> { it.startDate }.thenBy { it.event.title })
    }

    fun saveEvent(draft: EventDraft): Boolean {
        val startMinutes = if (draft.allDay) 0 else CalendarEvent.labelToMinutes(draft.startLabel) ?: return false
        val endMinutes = if (draft.allDay) 23 * 60 + 59 else CalendarEvent.labelToMinutes(draft.endLabel) ?: return false
        if (draft.title.isBlank()) return false
        if (draft.endDate.isBefore(draft.date)) return false
        if (!draft.allDay && draft.endDate == draft.date && endMinutes <= startMinutes) return false

        val event = CalendarEvent(
            id = draft.id ?: UUID.randomUUID().toString(),
            title = draft.title.trim(),
            location = draft.location.trim(),
            date = draft.date,
            endDate = draft.endDate,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            category = draft.category,
            people = draft.people.trim(),
            notes = draft.notes.trim(),
            allDay = draft.allDay,
            recurrenceRule = draft.recurrenceRule,
            customRepeatDays = draft.customRepeatDays.coerceAtLeast(1),
            recurrenceEndDate = draft.recurrenceEndDate,
            excludedDates = draft.excludedDates.distinct().sorted(),
            reminder = draft.reminder,
            done = draft.done,
            updatedAt = System.currentTimeMillis()
        )

        val existingIndex = eventItems.indexOfFirst { it.id == event.id }
        if (existingIndex >= 0) {
            eventItems[existingIndex] = event
        } else {
            eventItems.add(event)
        }
        persist()
        reminderScheduler?.schedule(event)
        return true
    }

    fun saveRecurringEdit(draft: EventDraft, mode: RecurrenceEditMode): Boolean {
        val originalId = draft.id ?: return saveEvent(draft)
        val originalIndex = eventItems.indexOfFirst { it.id == originalId }
        if (originalIndex < 0) return saveEvent(draft)
        val original = eventItems[originalIndex]
        if (original.recurrenceRule == RecurrenceRule.NONE || mode == RecurrenceEditMode.ENTIRE_SERIES) {
            return saveEvent(draft)
        }

        val startMinutes = if (draft.allDay) 0 else CalendarEvent.labelToMinutes(draft.startLabel) ?: return false
        val endMinutes = if (draft.allDay) 23 * 60 + 59 else CalendarEvent.labelToMinutes(draft.endLabel) ?: return false
        if (draft.title.isBlank()) return false
        if (draft.endDate.isBefore(draft.date)) return false
        if (!draft.allDay && draft.endDate == draft.date && endMinutes <= startMinutes) return false

        when (mode) {
            RecurrenceEditMode.SINGLE_OCCURRENCE -> {
                eventItems[originalIndex] = original.copy(
                    excludedDates = (original.excludedDates + draft.date).distinct().sorted(),
                    updatedAt = System.currentTimeMillis()
                )
                eventItems.add(
                    calendarEventFromDraft(
                        draft.copy(
                            id = null,
                            recurrenceRule = RecurrenceRule.NONE,
                            recurrenceEndDate = null,
                            excludedDates = emptyList()
                        )
                    )
                )
            }

            RecurrenceEditMode.THIS_AND_FUTURE -> {
                val previousEnd = draft.date.minusDays(1)
                eventItems[originalIndex] = original.copy(
                    recurrenceEndDate = previousEnd,
                    updatedAt = System.currentTimeMillis()
                )
                eventItems.add(
                    calendarEventFromDraft(
                        draft.copy(
                            id = null,
                            recurrenceEndDate = null,
                            excludedDates = emptyList()
                        )
                    )
                )
            }

            RecurrenceEditMode.ENTIRE_SERIES -> return saveEvent(draft)
        }
        persist()
        reminderScheduler?.scheduleAll(eventItems)
        return true
    }

    fun deleteEvent(id: String) {
        eventItems.removeAll { it.id == id }
        persist()
        reminderScheduler?.cancel(id)
    }

    fun toggleDone(id: String) {
        val index = eventItems.indexOfFirst { it.id == id }
        if (index < 0) return
        val event = eventItems[index]
        eventItems[index] = event.copy(done = !event.done, updatedAt = System.currentTimeMillis())
        persist()
    }

    fun generateEpaperPayload(): String {
        val zoneId = ZoneId.systemDefault()
        val array = JSONArray()
        upcomingEvents(limit = 12).forEach { event ->
            array.put(
                JSONObject()
                    .put("title", event.title)
                    .put("date", event.date.toString())
                    .put("endDate", event.endDate.toString())
                    .put("start", event.startLabel)
                    .put("end", event.endLabel)
                    .put("allDay", event.allDay)
                    .put("recurrence", event.recurrenceRule.name)
                    .put("reminder", event.reminder.name)
                    .put("category", event.category.name)
                    .put("people", event.people)
            )
        }

        val payload = JSONObject()
            .put("deviceView", "MONTH_WITH_TODAY_EVENTS")
            .put("generatedAt", ZonedDateTime.now(zoneId).toString())
            .put("timezone", zoneId.id)
            .put("currentDate", selectedDate.toString())
            .put("events", array)
            .toString(2)

        syncStatus = "Payload generado para ${array.length()} eventos"
        return payload
    }

    private fun persist() {
        if (persistChanges) repository.saveEvents(eventItems)
    }

    private fun calendarEventFromDraft(draft: EventDraft): CalendarEvent {
        val startMinutes = if (draft.allDay) 0 else CalendarEvent.labelToMinutes(draft.startLabel) ?: 0
        val endMinutes = if (draft.allDay) 23 * 60 + 59 else CalendarEvent.labelToMinutes(draft.endLabel) ?: startMinutes + 30
        return CalendarEvent(
            id = draft.id ?: UUID.randomUUID().toString(),
            title = draft.title.trim(),
            location = draft.location.trim(),
            date = draft.date,
            endDate = draft.endDate,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            category = draft.category,
            people = draft.people.trim(),
            notes = draft.notes.trim(),
            allDay = draft.allDay,
            recurrenceRule = draft.recurrenceRule,
            customRepeatDays = draft.customRepeatDays.coerceAtLeast(1),
            recurrenceEndDate = draft.recurrenceEndDate,
            excludedDates = draft.excludedDates.distinct().sorted(),
            reminder = draft.reminder,
            done = draft.done,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun CalendarEvent.matchesFilters(): Boolean {
        return matches(searchQuery) &&
            (categoryFilter == null || category == categoryFilter) &&
            matchesDateFilter(dateFilter)
    }

    private fun CalendarOccurrence.matchesFilters(): Boolean {
        return event.matches(searchQuery) &&
            (categoryFilter == null || event.category == categoryFilter) &&
            matchesDateFilter(dateFilter)
    }

    private fun CalendarEvent.matchesDateFilter(filter: DateFilter): Boolean {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
        val weekEnd = weekStart.plusDays(6)
        return when (filter) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> occurrencesOverlapping(today, today).isNotEmpty()
            DateFilter.THIS_WEEK -> occurrencesOverlapping(weekStart, weekEnd).isNotEmpty()
        }
    }

    private fun CalendarOccurrence.matchesDateFilter(filter: DateFilter): Boolean {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
        val weekEnd = weekStart.plusDays(6)
        return when (filter) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> includes(today)
            DateFilter.THIS_WEEK -> overlaps(weekStart, weekEnd)
        }
    }
}

enum class AppSection {
    AGENDA,
    WEEK,
    TASKS,
    CALENDAR
}

enum class DateFilter(val label: String) {
    ALL("Todo"),
    TODAY("Hoy"),
    THIS_WEEK("Esta semana")
}

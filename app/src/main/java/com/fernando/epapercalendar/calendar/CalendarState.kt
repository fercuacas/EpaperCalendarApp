package com.fernando.epapercalendar.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fernando.epapercalendar.epaper.EpaperPayloadBuilder
import com.fernando.epapercalendar.epaper.EpaperPayloadResult
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class CalendarState(
    private val repository: CalendarStore,
    private val reminderScheduler: CalendarReminderScheduler? = null,
    initialEvents: List<CalendarEvent>? = null,
    private val persistChanges: Boolean = true
) {
    private val eventItems = mutableStateListOf<CalendarEvent>()
    private val importedItems = mutableStateListOf<CalendarEvent>()
    private var importsReadyForPayload = true

    fun replaceImportedEvents(events: List<CalendarEvent>) {
        if (!persistChanges) return // The tutorial can never receive real device events.
        require(events.all { it.isReadOnly })
        importedItems.clear()
        importedItems.addAll(events.distinctBy { it.id })
    }

    fun setImportsReadyForPayload(ready: Boolean) {
        importsReadyForPayload = ready
    }

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

    val filtersEnabled: Boolean
        get() = section == AppSection.WEEK || section == AppSection.CALENDAR

    val events: List<CalendarEvent>
        get() = eventItems + importedItems

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

    fun navigateWeek(direction: Int) {
        require(direction == -1 || direction == 1)
        selectDate(selectedDate.plusWeeks(direction.toLong()))
    }

    fun navigateMonth(direction: Int) {
        require(direction == -1 || direction == 1)
        selectDate(selectedDate.plusMonths(direction.toLong()))
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

    fun upcomingEvents(limit: Int = 8): List<CalendarEvent> = events
        .flatMap { it.occurrencesOverlapping(selectedDate, selectedDate.plusMonths(6)) }
        .filter { it.matchesFilters() }
        .sortedWith(compareBy<CalendarOccurrence> { it.startDate }.thenBy { it.event.startMinutes })
        .map { it.asCalendarEvent() }
        .distinctBy { it.id }
        .take(limit)

    fun monthEvents(): List<CalendarEvent> = events
        .filter { event ->
            event.occurrencesOverlapping(visibleMonth.atDay(1), visibleMonth.atEndOfMonth())
                .any { it.matchesFilters() }
        }
        .sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.startMinutes })

    fun taskEvents(): List<CalendarEvent> = eventItems
        .filter { it.category == EventCategory.TASK && it.matchesFilters() }
        .sortedWith(compareBy<CalendarEvent> { it.done }.thenBy { it.date }.thenBy { it.startMinutes })

    fun occurrencesInRange(start: LocalDate, end: LocalDate): List<CalendarOccurrence> {
        return events
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
        if (draft.id?.startsWith("device:") == true || importedItems.any { it.id == draft.id }) return false
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
            customWeekDays = if (draft.recurrenceRule == RecurrenceRule.CUSTOM) draft.customWeekDays.toSet() else emptySet(),
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
        if (draft.id?.startsWith("device:") == true || importedItems.any { it.id == draft.id }) return false
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

    fun deleteEvent(
        id: String,
        occurrenceDate: LocalDate? = null,
        mode: RecurrenceDeleteMode = RecurrenceDeleteMode.ENTIRE_SERIES
    ): Boolean {
        if (id.startsWith("device:") || importedItems.any { it.id == id }) return false
        val index = eventItems.indexOfFirst { it.id == id }
        if (index < 0) return false
        val event = eventItems[index]
        if (mode == RecurrenceDeleteMode.SINGLE_OCCURRENCE && event.recurrenceRule != RecurrenceRule.NONE) {
            val date = occurrenceDate ?: return false
            if (event.occurrencesOverlapping(date, date).none { it.startDate == date }) return false
            val updated = event.copy(excludedDates = (event.excludedDates + date).distinct().sorted(),
                updatedAt = System.currentTimeMillis())
            eventItems[index] = updated
            persist()
            reminderScheduler?.schedule(updated)
            return true
        }
        eventItems.removeAt(index)
        persist()
        reminderScheduler?.cancel(id)
        return true
    }

    fun toggleDone(id: String) {
        if (id.startsWith("device:") || importedItems.any { it.id == id }) return
        val index = eventItems.indexOfFirst { it.id == id }
        if (index < 0) return
        val event = eventItems[index]
        eventItems[index] = event.copy(done = !event.done, updatedAt = System.currentTimeMillis())
        persist()
    }

    fun buildEpaperPayload(): EpaperPayloadResult {
        check(importsReadyForPayload) { "Actualiza los calendarios del móvil antes de preparar el envío." }
        val result = EpaperPayloadBuilder.build(events, selectedDate)
        syncStatus = "${result.occurrenceCount} apariciones · ${result.byteCount} bytes"
        return result
    }

    fun generateEpaperPayload(): String = buildEpaperPayload().json

    fun updateSyncStatus(status: String) {
        syncStatus = status
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
            customWeekDays = if (draft.recurrenceRule == RecurrenceRule.CUSTOM) draft.customWeekDays.toSet() else emptySet(),
            recurrenceEndDate = draft.recurrenceEndDate,
            excludedDates = draft.excludedDates.distinct().sorted(),
            reminder = draft.reminder,
            done = draft.done,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun CalendarEvent.matchesFilters(): Boolean {
        if (!filtersEnabled) return true
        return matches(searchQuery) &&
            (categoryFilter == null || category == categoryFilter) &&
            matchesDateFilter(dateFilter)
    }

    private fun CalendarOccurrence.matchesFilters(): Boolean {
        if (!filtersEnabled) return true
        return event.matches(searchQuery) &&
            (categoryFilter == null || event.category == categoryFilter) &&
            matchesDateFilter(dateFilter)
    }

    private fun CalendarEvent.matchesDateFilter(filter: DateFilter): Boolean {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)
        return when (filter) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> occurrencesOverlapping(today, today).isNotEmpty()
            DateFilter.THIS_WEEK -> occurrencesOverlapping(weekStart, weekEnd).isNotEmpty()
        }
    }

    private fun CalendarOccurrence.matchesDateFilter(filter: DateFilter): Boolean {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
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

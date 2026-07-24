package com.fernando.epapercalendar.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class CalendarRepository(context: Context) {
    private val preferences = context.getSharedPreferences("epaper_calendar", Context.MODE_PRIVATE)

    fun loadEvents(): List<CalendarEvent> {
        val rawEvents = preferences.getString(KEY_EVENTS, null) ?: return defaultEvents()
        return runCatching {
            val array = JSONArray(rawEvents)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(item.toCalendarEvent())
                }
            }
        }.getOrElse { defaultEvents() }
    }

    fun saveEvents(events: List<CalendarEvent>) {
        val array = JSONArray()
        events.sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.startMinutes })
            .forEach { event -> array.put(event.toJson()) }
        preferences.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    private fun JSONObject.toCalendarEvent(): CalendarEvent {
        val category = runCatching {
            EventCategory.valueOf(optString("category", EventCategory.WORK.name))
        }.getOrDefault(EventCategory.WORK)
        val recurrenceRule = runCatching {
            RecurrenceRule.valueOf(optString("recurrenceRule", RecurrenceRule.NONE.name))
        }.getOrDefault(RecurrenceRule.NONE)
        val reminder = runCatching {
            ReminderOption.valueOf(optString("reminder", ReminderOption.NONE.name))
        }.getOrDefault(ReminderOption.NONE)
        val startDate = LocalDate.parse(optString("date"))
        val recurrenceEndDate = optString("recurrenceEndDate", "")
            .takeIf { it.isNotBlank() }
            ?.let(LocalDate::parse)
        val excludedDates = optJSONArray("excludedDates")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    add(LocalDate.parse(array.getString(index)))
                }
            }
        }.orEmpty()

        return CalendarEvent(
            id = optString("id", UUID.randomUUID().toString()),
            title = optString("title"),
            location = optString("location"),
            date = startDate,
            endDate = LocalDate.parse(optString("endDate", startDate.toString())),
            startMinutes = optInt("startMinutes"),
            endMinutes = optInt("endMinutes"),
            category = category,
            people = optString("people"),
            notes = optString("notes"),
            allDay = optBoolean("allDay", false),
            recurrenceRule = recurrenceRule,
            customRepeatDays = optInt("customRepeatDays", 7).coerceAtLeast(1),
            recurrenceEndDate = recurrenceEndDate,
            excludedDates = excludedDates,
            reminder = reminder,
            done = optBoolean("done"),
            updatedAt = optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun CalendarEvent.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("location", location)
        .put("date", date.toString())
        .put("endDate", endDate.toString())
        .put("startMinutes", startMinutes)
        .put("endMinutes", endMinutes)
        .put("category", category.name)
        .put("people", people)
        .put("notes", notes)
        .put("allDay", allDay)
        .put("recurrenceRule", recurrenceRule.name)
        .put("customRepeatDays", customRepeatDays)
        .put("recurrenceEndDate", recurrenceEndDate?.toString().orEmpty())
        .put("excludedDates", JSONArray().also { array ->
            excludedDates.forEach { array.put(it.toString()) }
        })
        .put("reminder", reminder.name)
        .put("done", done)
        .put("updatedAt", updatedAt)

    fun loadSection(): AppSection {
        val rawSection = preferences.getString(KEY_SECTION, AppSection.AGENDA.name) ?: AppSection.AGENDA.name
        if (rawSection == "DAY") return AppSection.AGENDA
        return runCatching { AppSection.valueOf(rawSection) }.getOrDefault(AppSection.AGENDA)
    }

    fun saveSection(section: AppSection) {
        preferences.edit().putString(KEY_SECTION, section.name).apply()
    }

    fun loadCategoryFilter(): EventCategory? {
        val raw = preferences.getString(KEY_CATEGORY_FILTER, null) ?: return null
        return runCatching { EventCategory.valueOf(raw) }.getOrNull()
    }

    fun saveCategoryFilter(category: EventCategory?) {
        preferences.edit().apply {
            if (category == null) remove(KEY_CATEGORY_FILTER) else putString(KEY_CATEGORY_FILTER, category.name)
        }.apply()
    }

    fun loadDateFilter(): DateFilter {
        return runCatching {
            DateFilter.valueOf(preferences.getString(KEY_DATE_FILTER, DateFilter.ALL.name) ?: DateFilter.ALL.name)
        }.getOrDefault(DateFilter.ALL)
    }

    fun saveDateFilter(filter: DateFilter) {
        preferences.edit().putString(KEY_DATE_FILTER, filter.name).apply()
    }

    private fun defaultEvents(): List<CalendarEvent> {
        val today = LocalDate.now()
        return listOf(
            CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Design Sync",
                location = "Equipo producto",
                date = today,
                endDate = today,
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                category = EventCategory.WORK
            ),
            CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Café con Jamie",
                location = "Blue Bottle Coffee",
                date = today,
                endDate = today,
                startMinutes = 11 * 60,
                endMinutes = 11 * 60 + 45,
                category = EventCategory.COFFEE
            ),
            CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Deep Work",
                location = "Sesión de foco",
                date = today,
                endDate = today,
                startMinutes = 14 * 60,
                endMinutes = 16 * 60,
                category = EventCategory.FOCUS
            ),
            CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Evening Workout",
                location = "Gym & Fitness",
                date = today,
                endDate = today,
                startMinutes = 18 * 60 + 30,
                endMinutes = 19 * 60 + 30,
                category = EventCategory.FITNESS
            ),
            CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Preparar payload e-paper",
                location = "ESP32-C3",
                date = today.plusDays(1),
                endDate = today.plusDays(1),
                startMinutes = 10 * 60,
                endMinutes = 11 * 60,
                category = EventCategory.TASK
            ),
            CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Vacaciones",
                location = "Todo el día",
                date = today.plusDays(3),
                endDate = today.plusDays(7),
                startMinutes = 0,
                endMinutes = 23 * 60 + 59,
                category = EventCategory.PERSONAL,
                allDay = true,
                reminder = ReminderOption.DAY_BEFORE
            )
        )
    }

    private companion object {
        const val KEY_EVENTS = "events"
        const val KEY_SECTION = "selected_section"
        const val KEY_CATEGORY_FILTER = "category_filter"
        const val KEY_DATE_FILTER = "date_filter"
    }
}

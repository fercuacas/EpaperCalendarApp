package com.fernando.epapercalendar.calendar.device

import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.EventCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

data class DeviceCalendarIdentity(val id: Long, val accountName: String, val accountType: String)

data class DeviceCalendarInfo(
    val identity: DeviceCalendarIdentity,
    val name: String,
    val color: Int
)

data class DeviceCalendarSelection(
    val identity: DeviceCalendarIdentity,
    val category: EventCategory = EventCategory.PERSONAL
)

data class DeviceEventSource(
    val calendar: DeviceCalendarIdentity,
    val calendarName: String,
    val eventId: Long,
    val beginMillis: Long
)

data class DeviceCalendarRange(val start: LocalDate, val endInclusive: LocalDate) {
    companion object {
        fun around(date: LocalDate): DeviceCalendarRange {
            val month = YearMonth.from(date)
            return DeviceCalendarRange(month.minusMonths(6).atDay(1), month.plusMonths(6).atEndOfMonth())
        }
    }
}

data class DeviceImportSnapshot(
    val range: DeviceCalendarRange,
    val zoneId: String,
    val updatedAt: Long,
    val events: List<CalendarEvent>
)

data class DeviceCalendarData(
    val selections: List<DeviceCalendarSelection> = emptyList(),
    val snapshot: DeviceImportSnapshot? = null
)

data class DeviceOccurrence(
    val eventId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    val title: String,
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val cancelled: Boolean = false,
    val deleted: Boolean = false
) {
    fun toEvent(calendar: DeviceCalendarInfo, category: EventCategory, zoneId: ZoneId): CalendarEvent? {
        if (cancelled || deleted || endMillis < beginMillis) return null
        val zone = if (allDay) ZoneOffset.UTC else zoneId
        val start = Instant.ofEpochMilli(beginMillis).atZone(zone)
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        // Android uses exclusive ends. Midnight must not create a spurious day.
        val last = Instant.ofEpochMilli(if (endMillis > beginMillis) endMillis - 1 else beginMillis).atZone(zone)
        val endMinutes = if (allDay || (endMillis > beginMillis && end.toLocalTime() == java.time.LocalTime.MIDNIGHT)) {
            23 * 60 + 59
        } else end.hour * 60 + end.minute
        return CalendarEvent(
            id = "device:${calendar.identity.id}:$eventId:$beginMillis",
            title = title.ifBlank { "Sin título" },
            location = location,
            date = start.toLocalDate(),
            endDate = last.toLocalDate(),
            startMinutes = if (allDay) 0 else start.hour * 60 + start.minute,
            endMinutes = if (allDay) 23 * 60 + 59 else endMinutes,
            category = category.takeUnless { it == EventCategory.TASK } ?: EventCategory.PERSONAL,
            notes = description,
            allDay = allDay,
            updatedAt = 0,
            deviceSource = DeviceEventSource(calendar.identity, calendar.name, eventId, beginMillis)
        )
    }
}

interface DeviceCalendarReader {
    fun hasPermission(): Boolean
    suspend fun calendars(): List<DeviceCalendarInfo>
    suspend fun occurrences(
        calendars: List<DeviceCalendarInfo>,
        selections: List<DeviceCalendarSelection>,
        range: DeviceCalendarRange,
        zoneId: ZoneId
    ): List<CalendarEvent>
}

interface DeviceCalendarStorage {
    suspend fun load(): DeviceCalendarData
    suspend fun save(data: DeviceCalendarData)
}

package com.fernando.epapercalendar.calendar.device

import android.content.Context
import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.EventCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class DeviceCalendarRepository(context: Context) : DeviceCalendarStorage {
    private val preferences = context.applicationContext.getSharedPreferences("device_calendar_import", Context.MODE_PRIVATE)

    override suspend fun load(): DeviceCalendarData = withContext(Dispatchers.IO) {
        DeviceCalendarCodec.decode(preferences.getString("data", null))
    }

    override suspend fun save(data: DeviceCalendarData) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString("data", DeviceCalendarCodec.encode(data)).commit()) {
            "No se pudo guardar la copia de los calendarios."
        }
    }
}

/** Separate, versioned cache: never uses the local event repository or its demo fallback. */
object DeviceCalendarCodec {
    fun encode(data: DeviceCalendarData): String {
        val root = JSONObject().put("version", 1).put("selections", JSONArray().also { array ->
            data.selections.forEach { array.put(identityJson(it.identity).put("category", it.category.name)) }
        })
        data.snapshot?.let { snapshot ->
            root.put("snapshot", JSONObject()
                .put("start", snapshot.range.start.toString()).put("end", snapshot.range.endInclusive.toString())
                .put("zone", snapshot.zoneId).put("updatedAt", snapshot.updatedAt)
                .put("events", JSONArray().also { array ->
                    snapshot.events.forEach { event ->
                        val source = requireNotNull(event.deviceSource)
                        array.put(JSONObject().put("id", event.id).put("title", event.title)
                            .put("location", event.location).put("description", event.notes)
                            .put("date", event.date.toString()).put("endDate", event.endDate.toString())
                            .put("startMinutes", event.startMinutes).put("endMinutes", event.endMinutes)
                            .put("allDay", event.allDay).put("category", event.category.name)
                            .put("source", identityJson(source.calendar).put("name", source.calendarName)
                                .put("eventId", source.eventId).put("begin", source.beginMillis)))
                    }
                }))
        }
        return root.toString()
    }

    fun decode(raw: String?): DeviceCalendarData {
        if (raw == null) return DeviceCalendarData()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return DeviceCalendarData()
        if (root.optInt("version") != 1) return DeviceCalendarData()
        val selections = runCatching {
            val array = root.getJSONArray("selections")
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                DeviceCalendarSelection(identity(item), category(item))
            }.distinctBy { it.identity }
        }.getOrDefault(emptyList())
        val snapshot = runCatching {
            val item = root.optJSONObject("snapshot") ?: return@runCatching null
            val range = DeviceCalendarRange(LocalDate.parse(item.getString("start")), LocalDate.parse(item.getString("end")))
            require(!range.endInclusive.isBefore(range.start))
            val events = item.getJSONArray("events")
            DeviceImportSnapshot(range, item.getString("zone"), item.getLong("updatedAt"),
                List(events.length()) { index ->
                    val event = events.getJSONObject(index)
                    val source = event.getJSONObject("source")
                    CalendarEvent(
                        id = event.getString("id"), title = event.getString("title"),
                        location = event.getString("location"), notes = event.getString("description"),
                        date = LocalDate.parse(event.getString("date")), endDate = LocalDate.parse(event.getString("endDate")),
                        startMinutes = event.getInt("startMinutes"), endMinutes = event.getInt("endMinutes"),
                        allDay = event.getBoolean("allDay"), category = category(event), updatedAt = 0,
                        deviceSource = DeviceEventSource(identity(source), source.getString("name"),
                            source.getLong("eventId"), source.getLong("begin"))
                    )
                }.filter { event -> selections.any { it.identity == event.deviceSource?.calendar } }.distinctBy { it.id })
        }.getOrNull()
        return DeviceCalendarData(selections, snapshot)
    }

    private fun category(item: JSONObject): EventCategory = runCatching {
        EventCategory.valueOf(item.getString("category"))
    }.getOrDefault(EventCategory.PERSONAL).takeUnless { it == EventCategory.TASK } ?: EventCategory.PERSONAL

    private fun identityJson(identity: DeviceCalendarIdentity) = JSONObject().put("calendarId", identity.id)
        .put("account", identity.accountName).put("accountType", identity.accountType)

    private fun identity(item: JSONObject) = DeviceCalendarIdentity(item.getLong("calendarId"),
        item.getString("account"), item.getString("accountType"))
}

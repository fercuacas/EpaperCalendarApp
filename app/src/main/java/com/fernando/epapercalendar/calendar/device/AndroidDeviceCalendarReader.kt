package com.fernando.epapercalendar.calendar.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as CalendarColumns
import android.provider.CalendarContract.Instances as InstanceColumns
import androidx.core.content.ContextCompat
import com.fernando.epapercalendar.calendar.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZoneOffset

class AndroidDeviceCalendarReader(context: Context) : DeviceCalendarReader {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    override fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    override suspend fun calendars(): List<DeviceCalendarInfo> = withContext(Dispatchers.IO) {
        checkPermission()
        val projection = arrayOf(CalendarColumns._ID, CalendarColumns.ACCOUNT_NAME, CalendarColumns.ACCOUNT_TYPE,
            CalendarColumns.CALENDAR_DISPLAY_NAME, CalendarColumns.CALENDAR_COLOR)
        val cursor = resolver.query(CalendarColumns.CONTENT_URI, projection, null, null, null)
            ?: error("El proveedor de calendarios no está disponible.")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(DeviceCalendarInfo(
                        DeviceCalendarIdentity(it.getLong(0), it.getString(1).orEmpty(), it.getString(2).orEmpty()),
                        it.getString(3).orEmpty().ifBlank { "Calendario" }, it.getInt(4)
                    ))
                }
            }.sortedWith(compareBy({ it.identity.accountName }, { it.name }, { it.identity.id }))
        }
    }

    override suspend fun occurrences(
        calendars: List<DeviceCalendarInfo>,
        selections: List<DeviceCalendarSelection>,
        range: DeviceCalendarRange,
        zoneId: ZoneId
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        checkPermission()
        if (selections.isEmpty()) return@withContext emptyList()
        val selected = selections.associateBy { it.identity.id }
        val infos = calendars.associateBy { it.identity.id }
        // Include both local timed boundaries and UTC all-day boundaries.
        val begin = minOf(range.start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            range.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val end = maxOf(range.endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            range.endInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(InstanceColumns.EVENT_ID, InstanceColumns.CALENDAR_ID, InstanceColumns.BEGIN,
            InstanceColumns.END, InstanceColumns.TITLE, InstanceColumns.EVENT_LOCATION, InstanceColumns.DESCRIPTION,
            InstanceColumns.ALL_DAY, InstanceColumns.STATUS, CalendarContract.Events.DELETED)
        val ids = selected.keys.toList()
        val cursor = resolver.query(uri, projection,
            "${InstanceColumns.CALENDAR_ID} IN (${ids.joinToString(",") { "?" }})",
            ids.map(Long::toString).toTypedArray(), "${InstanceColumns.BEGIN} ASC")
            ?: error("No se pudieron leer los eventos del móvil.")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val selection = selected[it.getLong(1)] ?: continue
                    val info = infos[it.getLong(1)] ?: continue
                    if (info.identity != selection.identity) continue
                    val event = DeviceOccurrence(
                        eventId = it.getLong(0), beginMillis = it.getLong(2), endMillis = it.getLong(3),
                        title = it.getString(4).orEmpty(), location = it.getString(5).orEmpty(),
                        description = it.getString(6).orEmpty(), allDay = it.getInt(7) != 0,
                        cancelled = it.getInt(8) == CalendarContract.Events.STATUS_CANCELED,
                        deleted = it.getInt(9) != 0
                    ).toEvent(info, selection.category, zoneId) ?: continue
                    if (!event.endDate.isBefore(range.start) && !event.date.isAfter(range.endInclusive)) add(event)
                }
            }.distinctBy { it.id }
        }
    }

    private fun checkPermission() {
        if (!hasPermission()) throw SecurityException("Permite leer el calendario desde Calendarios del móvil.")
    }
}

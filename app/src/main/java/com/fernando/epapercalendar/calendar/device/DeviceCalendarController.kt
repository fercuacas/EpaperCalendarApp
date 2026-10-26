package com.fernando.epapercalendar.calendar.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.CalendarState
import com.fernando.epapercalendar.calendar.EventCategory
import com.fernando.epapercalendar.epaper.EpaperPayloadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** Calls and state publication belong to the UI dispatcher; reader/storage perform their own IO. */
class DeviceCalendarController(
    private val reader: DeviceCalendarReader,
    private val storage: DeviceCalendarStorage,
    private val onEvents: (List<CalendarEvent>) -> Unit,
    private val onReadiness: (Boolean) -> Unit = {},
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    var calendars by mutableStateOf<List<DeviceCalendarInfo>>(emptyList())
        private set
    var selections by mutableStateOf<List<DeviceCalendarSelection>>(emptyList())
        private set
    var snapshot by mutableStateOf<DeviceImportSnapshot?>(null)
        private set
    var permissionGranted by mutableStateOf(reader.hasPermission())
        private set
    var busy by mutableStateOf(false)
        private set
    var initialized by mutableStateOf(false)
        private set
    var readyForPayload by mutableStateOf(true)
        private set
    var status by mutableStateOf("Elige qué calendarios del móvil quieres mostrar.")
        private set
    private var range = DeviceCalendarRange.around(LocalDate.now())
    private var revision = 0L
    private val mutex = Mutex()

    fun selectDate(date: LocalDate) {
        val next = DeviceCalendarRange.around(date)
        if (next != range || snapshot?.zoneId?.let { it != zone().id } == true) {
            range = next
            revision++
            publishReadiness(selections.isEmpty())
        }
    }

    suspend fun initialize() {
        if (initialized) return
        mutex.withLock {
            if (initialized) return@withLock
            val data = storage.load()
            selections = data.selections
            permissionGranted = reader.hasPermission()
            snapshot = if (permissionGranted) data.snapshot else null
            if (!permissionGranted && data.snapshot != null) storage.save(DeviceCalendarData(selections))
            onEvents(snapshot?.events.orEmpty())
            publishReadiness(selections.isEmpty())
            initialized = true
        }
    }

    suspend fun selectCalendar(identity: DeviceCalendarIdentity, category: EventCategory?) {
        initialize()
        require(category != EventCategory.TASK)
        revision++ // Invalidates an in-flight query before waiting for its mutex.
        selections = selections.filterNot { it.identity == identity } +
            if (category == null) emptyList() else listOf(DeviceCalendarSelection(identity, category))
        snapshot = snapshot?.copy(events = snapshot!!.events.mapNotNull { event ->
            val selected = selections.firstOrNull { it.identity == event.deviceSource?.calendar }
            selected?.let { event.copy(category = it.category) }
        })
        onEvents(snapshot?.events.orEmpty())
        publishReadiness(selections.isEmpty())
        status = "Selección guardada. Pulsa Guardar y actualizar para leer los eventos."
        mutex.withLock { storage.save(DeviceCalendarData(selections, snapshot)) }
    }

    suspend fun disconnect() {
        initialize()
        revision++
        selections = emptyList()
        snapshot = null
        onEvents(emptyList())
        publishReadiness(true)
        status = "Calendarios desconectados. Tus eventos locales no han cambiado."
        mutex.withLock { storage.save(DeviceCalendarData()) }
    }

    suspend fun refresh(): Boolean {
        try {
            initialize()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publishReadiness(false)
            status = "No se pudo cargar la configuración de calendarios. Inténtalo de nuevo."
            return false
        }
        return mutex.withLock {
            busy = true
            publishReadiness(selections.isEmpty())
            val requestRevision = revision
            try {
                permissionGranted = reader.hasPermission()
                if (!permissionGranted) {
                    revokePermission()
                    return@withLock selections.isEmpty()
                }
                status = "Leyendo calendarios del móvil…"
                val found = reader.calendars()
                if (requestRevision != revision) return@withLock false
                calendars = found
                val identities = found.map { it.identity }.toSet()
                selections = selections.filter { it.identity in identities }
                // A successful enumeration can prove that an account/calendar was removed.
                val retained = snapshot?.events.orEmpty().filter { event ->
                    selections.any { it.identity == event.deviceSource?.calendar }
                }
                snapshot = snapshot?.copy(events = retained)
                onEvents(retained)
                if (selections.isEmpty()) {
                    snapshot = null
                    storage.save(DeviceCalendarData())
                    if (requestRevision != revision) return@withLock false
                    publishReadiness(true)
                    status = "${found.size} calendarios disponibles. Selecciona los que quieras mostrar."
                    return@withLock true
                }
                val requestedRange = range
                val requestedZone = zone()
                val events = reader.occurrences(found, selections, requestedRange, requestedZone)
                if (!reader.hasPermission()) {
                    revokePermission()
                    return@withLock false
                }
                if (requestRevision != revision || requestedRange != range || requestedZone != zone()) return@withLock false
                val next = DeviceImportSnapshot(requestedRange, requestedZone.id, now(), events.distinctBy { it.id })
                storage.save(DeviceCalendarData(selections, next))
                if (requestRevision != revision) return@withLock false
                snapshot = next
                onEvents(next.events)
                publishReadiness(true)
                status = "Actualizado: ${next.events.size} apariciones."
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                revokePermission()
                false
            } catch (_: Exception) {
                publishReadiness(selections.isEmpty())
                status = "No se pudo actualizar. La última copia puede estar desactualizada; no se enviará al e-paper."
                false
            } finally {
                busy = false
            }
        }
    }

    suspend fun prepareEpaperPayload(state: CalendarState): EpaperPayloadResult {
        selectDate(state.selectedDate)
        initialize()
        // No provider reads are required for a purely local calendar.
        if (selections.isNotEmpty()) check(refresh()) { status }
        return state.buildEpaperPayload()
    }

    private suspend fun revokePermission() {
        permissionGranted = false
        calendars = emptyList()
        snapshot = null
        onEvents(emptyList())
        publishReadiness(selections.isEmpty())
        storage.save(DeviceCalendarData(selections))
        status = "Sin permiso de calendario. Permite el acceso desde Calendarios del móvil."
    }

    private fun publishReadiness(ready: Boolean) {
        readyForPayload = ready
        onReadiness(ready)
    }
}

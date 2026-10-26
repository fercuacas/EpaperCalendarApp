package com.fernando.epapercalendar.ui

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri
import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.CalendarState
import com.fernando.epapercalendar.calendar.EventCategory
import com.fernando.epapercalendar.calendar.device.AndroidDeviceCalendarReader
import com.fernando.epapercalendar.calendar.device.DeviceCalendarController
import com.fernando.epapercalendar.calendar.device.DeviceCalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun rememberDeviceCalendars(state: CalendarState, enabled: Boolean): DeviceCalendarController {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(state) {
        DeviceCalendarController(AndroidDeviceCalendarReader(context), DeviceCalendarRepository(context),
            state::replaceImportedEvents, state::setImportsReadyForPayload)
    }
    // The preflight also invalidates the range before every e-paper action.
    LaunchedEffect(YearMonth.from(state.selectedDate), enabled) {
        if (enabled) {
            controller.selectDate(state.selectedDate)
            controller.refresh()
        }
    }
    DisposableEffect(lifecycle, enabled, controller.permissionGranted) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var refreshJob: Job? = null
        var registered = false
        fun update() {
            refreshJob?.cancel()
            refreshJob = scope.launch { delay(500); controller.refresh() }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { update() }
        }
        fun stop() {
            refreshJob?.cancel()
            if (registered) context.contentResolver.unregisterContentObserver(observer)
            registered = false
        }
        fun start() {
            if (!enabled) return
            if (!registered && AndroidDeviceCalendarReader(context).hasPermission()) {
                try {
                    context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
                    registered = true
                } catch (_: SecurityException) { /* refresh removes unauthorized cached data */ }
            }
            update()
        }
        val listener = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(listener)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(listener); stop(); scope.cancel() }
    }
    return controller
}

@Composable
internal fun DeviceCalendarsDialog(controller: DeviceCalendarController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalCalendarPalette.current
    val scope = rememberCoroutineScope()
    var confirmDisconnect by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    fun action(block: suspend () -> Unit) {
        scope.launch {
            actionError = null
            try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { actionError = "No se pudo guardar la configuración. Inténtalo de nuevo." }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        action { controller.refresh() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Calendarios del móvil", color = palette.text) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Solo lectura: no modificamos ni borramos eventos en Google ni en otros calendarios.", color = palette.text)
                Text("Actualizar lee la copia que Android ya tiene. Para recibir cambios de Google, activa la sincronización de calendarios en el móvil y conéctate a Internet.", color = palette.textMuted)
                Text(actionError ?: controller.status, color = palette.textMuted)
                if (!controller.permissionGranted) {
                    Button(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Permitir acceso") }
                    if (denied) {
                        Text("Si Android no vuelve a preguntar, activa el permiso Calendario en los ajustes de la aplicación.", color = palette.textMuted)
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri()))
                        }) { Text("Abrir permisos de la app") }
                    }
                } else {
                    controller.calendars.groupBy { it.identity.accountName to it.identity.accountType }.forEach { (account, calendars) ->
                        Text(account.first.ifBlank { "Calendarios locales" }, color = palette.text, fontWeight = FontWeight.Bold)
                        if (account.second.isNotBlank()) Text(account.second, color = palette.textMuted)
                        calendars.forEach { calendar ->
                            val selected = controller.selections.firstOrNull { it.identity == calendar.identity }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selected != null, enabled = controller.initialized,
                                        modifier = Modifier.semantics {
                                            contentDescription = "Mostrar ${calendar.name}, ${calendar.identity.accountName}"
                                        },
                                        onCheckedChange = { checked -> action {
                                            controller.selectCalendar(calendar.identity, if (checked) EventCategory.PERSONAL else null)
                                        } })
                                    Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
                                    Text(calendar.name, modifier = Modifier.padding(start = 8.dp), color = palette.text)
                                }
                                if (selected != null) {
                                    var expanded by remember(calendar.identity) { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { expanded = true }) { Text("Categoría: ${selected.category.label}") }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            EventCategory.entries.filterNot { it == EventCategory.TASK }.forEach { category ->
                                                DropdownMenuItem(text = { Text(category.label) }, onClick = {
                                                    expanded = false
                                                    action { controller.selectCalendar(calendar.identity, category) }
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    controller.snapshot?.let { snapshot ->
                        val time = Instant.ofEpochMilli(snapshot.updatedAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        Text("Última actualización: $time · ${snapshot.events.size} apariciones", color = palette.textMuted)
                    }
                    Button(enabled = !controller.busy && controller.initialized,
                        onClick = { action { controller.refresh() } }) { Text("Guardar y actualizar") }
                    OutlinedButton(enabled = !controller.busy, onClick = { action { controller.refresh() } }) { Text("Actualizar ahora") }
                }
                if (controller.selections.isNotEmpty() || controller.snapshot != null) {
                    TextButton(onClick = { confirmDisconnect = true }) { Text("Desconectar calendarios") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text("¿Desconectar calendarios?") },
        text = { Text("Se borrarán la selección y la copia importada de esta app. Los eventos locales y los calendarios originales no cambiarán.") },
        confirmButton = { TextButton(onClick = { confirmDisconnect = false; action { controller.disconnect() } }) { Text("Desconectar") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } }
    )
}

@Composable
internal fun ImportedEventDialog(event: CalendarEvent, onDismiss: () -> Unit) {
    val palette = LocalCalendarPalette.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = palette.surface,
        title = { Text(event.title, color = palette.text) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Solo lectura · ${event.deviceSource?.calendarName.orEmpty()}", color = palette.accent)
                Text("${event.date} — ${event.endDate}", color = palette.text)
                Text(if (event.allDay) "Todo el día" else "${event.startLabel} — ${event.endLabel}", color = palette.text)
                if (event.location.isNotBlank()) Text(event.location, color = palette.text)
                if (event.notes.isNotBlank()) Text(event.notes, color = palette.text)
                Text("Para modificarlo, utiliza tu aplicación de calendario original y después actualiza aquí.", color = palette.textMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
internal fun ImportedCalendarLabel(event: CalendarEvent) {
    event.deviceSource?.let { source ->
        Text("${source.calendarName} · Solo lectura", color = LocalCalendarPalette.current.textMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

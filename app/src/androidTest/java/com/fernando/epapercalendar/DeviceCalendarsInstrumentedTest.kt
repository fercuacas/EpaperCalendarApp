package com.fernando.epapercalendar

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fernando.epapercalendar.calendar.*
import com.fernando.epapercalendar.calendar.device.*
import com.fernando.epapercalendar.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceCalendarsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val info = DeviceCalendarInfo(DeviceCalendarIdentity(12, "Cuenta ficticia", "com.google"), "Calendario de prueba", 0xff6699cc.toInt())

    @Test fun menuHasSeparateEntryAndTutorialDisablesConnections() {
        compose.setContent {
            MaterialTheme {
                AppMenuDialog(CalendarThemeOption.Warm, "Listo", {}, {}, {}, {},
                    connectionsEnabled = false, onDismiss = {})
            }
        }
        compose.onNodeWithText("Elegir calendarios").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Configurar y sincronizar").performScrollTo().assertIsNotEnabled()
    }

    @Test fun permissionIsAnExplicitActionAndCalendarSelectionIsShown() {
        val controller = controller(granted = false)
        compose.setContent { MaterialTheme { DeviceCalendarsDialog(controller) {} } }
        compose.onNodeWithText("Permitir acceso").assertExists()
        compose.onNodeWithText("Guardar y actualizar").assertDoesNotExist()
    }

    @Test fun selectingAccountsAndDisconnectConfirmationAreVisible() {
        val controller = controller(granted = true)
        runBlocking {
            controller.refresh()
            controller.selectCalendar(info.identity, EventCategory.PERSONAL)
            controller.refresh()
        }
        compose.setContent { MaterialTheme { DeviceCalendarsDialog(controller) {} } }
        compose.onNodeWithText("Cuenta ficticia").assertExists()
        compose.onNodeWithText("Calendario de prueba").assertExists()
        compose.onNodeWithText("Categoría: Personal").performScrollTo().assertExists()
        compose.onNodeWithText("Desconectar calendarios").performScrollTo().performClick()
        compose.onNodeWithText("¿Desconectar calendarios?").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, controller.selections.size) }
    }

    @Test fun importedDetailsHaveNoMutationActions() {
        val event = CalendarEvent("device:12:1:0", "Solo consulta", "Lugar", LocalDate.of(2026, 10, 23),
            startMinutes = 600, endMinutes = 660, category = EventCategory.PERSONAL,
            deviceSource = DeviceEventSource(info.identity, info.name, 1, 0))
        compose.setContent { MaterialTheme { ImportedEventDialog(event) {} } }
        compose.onNodeWithText("Solo consulta").assertExists()
        compose.onNodeWithText("Solo lectura · Calendario de prueba").assertExists()
        compose.onNodeWithText("Eliminar").assertDoesNotExist()
        compose.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test fun oldLocalJsonAndDeviceCacheUseIndependentPreferences() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "calendar_test_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            isolated.getSharedPreferences("epaper_calendar", 0).edit().putString("events",
                """[{"id":"legacy","title":"Antiguo","date":"2026-10-23","startMinutes":600,"endMinutes":660}]""").commit()
            val local = CalendarRepository(isolated)
            val legacy = local.loadEvents().single()
            assertFalse(legacy.isReadOnly)
            assertEquals(legacy.date, legacy.endDate)
            val imported = legacy.copy(id = "device:12:1:0", category = EventCategory.PERSONAL, updatedAt = 0,
                deviceSource = DeviceEventSource(info.identity, info.name, 1, 0))
            val device = DeviceCalendarRepository(isolated)
            val data = DeviceCalendarData(listOf(DeviceCalendarSelection(info.identity)),
                DeviceImportSnapshot(DeviceCalendarRange.around(legacy.date), "Europe/Madrid", 1, listOf(imported)))
            device.save(data)
            assertEquals(data, device.load())
            local.saveEvents(listOf(legacy, imported))
            assertEquals(listOf(legacy), local.loadEvents())
            device.save(DeviceCalendarData())
            assertEquals(listOf(legacy), local.loadEvents())
        } finally {
            base.deleteSharedPreferences(prefix + "epaper_calendar")
            base.deleteSharedPreferences(prefix + "device_calendar_import")
        }
    }

    private fun controller(granted: Boolean): DeviceCalendarController {
        val reader = object : DeviceCalendarReader {
            override fun hasPermission() = granted
            override suspend fun calendars() = listOf(info)
            override suspend fun occurrences(calendars: List<DeviceCalendarInfo>, selections: List<DeviceCalendarSelection>,
                range: DeviceCalendarRange, zoneId: ZoneId): List<CalendarEvent> = emptyList()
        }
        val storage = object : DeviceCalendarStorage {
            private var data = DeviceCalendarData()
            override suspend fun load() = data
            override suspend fun save(data: DeviceCalendarData) { this.data = data }
        }
        return DeviceCalendarController(reader, storage, {})
    }
}

package com.fernando.epapercalendar

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fernando.epapercalendar.calendar.*
import com.fernando.epapercalendar.ui.AddEventPanel
import com.fernando.epapercalendar.ui.RecurrenceDeleteDialog
import com.fernando.epapercalendar.ui.WeekdaySelector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecurrenceInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 23)

    @Test fun weekdaySelectorSupportsMultipleDaysAndEmptyValidation() {
        val selected = mutableStateOf(setOf(DayOfWeek.MONDAY))
        compose.setContent { MaterialTheme { WeekdaySelector(selected.value) { selected.value = it } } }
        compose.onNodeWithContentDescription("Repetir los lunes").assertIsSelected().performClick()
        compose.onNodeWithText("Selecciona al menos un día de la semana.").assertExists()
        compose.onNodeWithContentDescription("Repetir los miércoles").performClick()
        compose.onNodeWithContentDescription("Repetir los viernes").performClick()
        compose.runOnIdle { assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), selected.value) }
    }

    @Test fun editorSavesSelectedWeekdaysAndDisablesSavingEmptySelection() {
        var saved: EventDraft? = null
        val source = EventDraft(title = "Clase", date = date, endDate = date, recurrenceRule = RecurrenceRule.CUSTOM,
            customWeekDays = setOf(DayOfWeek.MONDAY))
        compose.setContent {
            MaterialTheme {
                AddEventPanel(true, source, date, {}, { saved = it; true }, { _, _ -> false }, { _, _, _ -> false })
            }
        }
        compose.onNodeWithContentDescription("Repetir los lunes").performScrollTo().performClick()
        compose.onNode(hasText("Crear evento") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Repetir los martes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Repetir los sábados").performScrollTo().performClick()
        compose.onNode(hasText("Crear evento") and hasClickAction()).performClick()
        compose.runOnIdle {
            assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), saved!!.customWeekDays)
            assertEquals(RecurrenceRule.CUSTOM, saved!!.recurrenceRule)
        }
    }

    @Test fun legacyIntervalEditorDoesNotSilentlyBecomeWeekdayRecurrence() {
        var saved: EventDraft? = null
        val source = EventDraft(id = "legacy", title = "Cada tres", date = date, endDate = date,
            recurrenceRule = RecurrenceRule.CUSTOM, customRepeatDays = 3)
        compose.setContent {
            MaterialTheme {
                AddEventPanel(true, source, date, {}, { false }, { draft, _ -> saved = draft; true }, { _, _, _ -> false })
            }
        }
        compose.onNodeWithText("3 días").performScrollTo().assertExists()
        compose.onNodeWithText("Repetir cada semana los días:").assertDoesNotExist()
        compose.onNodeWithText("Guardar cambios").performClick()
        compose.onNodeWithText("Toda la serie").performClick()
        compose.runOnIdle {
            assertTrue(saved!!.customWeekDays.isEmpty())
            assertEquals(3, saved!!.customRepeatDays)
        }
    }

    @Test fun recurringDeleteRequiresChoiceAndCancelDoesNothing() {
        var deletedDate: LocalDate? = null
        var mode: RecurrenceDeleteMode? = null
        val source = EventDraft(id = "series", title = "Repetido", date = date, endDate = date,
            recurrenceRule = RecurrenceRule.CUSTOM, customWeekDays = setOf(DayOfWeek.FRIDAY))
        compose.setContent {
            MaterialTheme {
                AddEventPanel(true, source, date, {}, { false }, { _, _ -> false }, { _, occurrence, selectedMode ->
                    deletedDate = occurrence; mode = selectedMode; true
                })
            }
        }
        compose.onNodeWithText("Borrar").performScrollTo().performClick()
        compose.onNodeWithText("Borrar evento repetido").assertExists()
        compose.onNodeWithText("Solo este día").assertExists()
        compose.onNodeWithText("Toda la serie").assertExists()
        compose.runOnIdle { assertNull(mode) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertNull(mode) }
        compose.onNodeWithText("Borrar").performScrollTo().performClick()
        compose.onNodeWithText("Solo este día").performClick()
        compose.runOnIdle {
            assertEquals(date, deletedDate)
            assertEquals(RecurrenceDeleteMode.SINGLE_OCCURRENCE, mode)
        }
    }

    @Test fun wholeSeriesOptionAndMultidayOccurrenceExplanation() {
        var mode: RecurrenceDeleteMode? = null
        compose.setContent { MaterialTheme { RecurrenceDeleteDialog(date, date.plusDays(2), { mode = it }, {}) } }
        compose.onNodeWithText("Solo la aparición del 23/10/2026 al 25/10/2026; el resto continúa.").assertExists()
        compose.onNodeWithText("Toda la serie").performClick()
        compose.runOnIdle { assertEquals(RecurrenceDeleteMode.ENTIRE_SERIES, mode) }
    }

    @Test fun repositoryPersistsWeekdaysAndReadsLegacyIntervalJson() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "recurrence_test_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            val repository = CalendarRepository(isolated)
            val event = CalendarEvent("weekdays", "Semanal", "", date, startMinutes = 540, endMinutes = 600,
                category = EventCategory.WORK, recurrenceRule = RecurrenceRule.CUSTOM,
                customWeekDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), excludedDates = listOf(date), updatedAt = 0)
            repository.saveEvents(listOf(event))
            assertEquals(event, CalendarRepository(isolated).loadEvents().single())
            isolated.getSharedPreferences("epaper_calendar", 0).edit().putString("events",
                """[{"id":"old","title":"Antiguo","date":"2026-10-23","recurrenceRule":"CUSTOM","customRepeatDays":3,"startMinutes":540,"endMinutes":600}]""").commit()
            val legacy = repository.loadEvents().single()
            assertTrue(legacy.customWeekDays.isEmpty())
            assertEquals(3, legacy.customRepeatDays)
            assertEquals(listOf(date, date.plusDays(3), date.plusDays(6)),
                legacy.occurrencesOverlapping(date, date.plusDays(8)).map { it.startDate })
        } finally {
            base.deleteSharedPreferences(prefix + "epaper_calendar")
        }
    }
}

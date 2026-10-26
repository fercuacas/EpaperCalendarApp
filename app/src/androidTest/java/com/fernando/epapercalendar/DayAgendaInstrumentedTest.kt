package com.fernando.epapercalendar

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fernando.epapercalendar.calendar.*
import com.fernando.epapercalendar.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DayAgendaInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 23)
    private fun event(id: String, start: Int = 540, end: Int = 600) = CalendarEvent(
        id, id, "Oficina", date, startMinutes = start, endMinutes = end, category = EventCategory.WORK)

    @Test fun summaryOnlyShowsTaskCountWithoutScrollableEventCards() {
        compose.setContent { MaterialTheme { SummaryPanel(2) } }
        compose.onNodeWithText("2 tareas pendientes").assertIsDisplayed()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("Día libre").assertDoesNotExist()
    }

    @Test fun all24HoursCanBeReachedStartingAtEightAndChangingDayResetsScroll() {
        val selected = mutableStateOf(date)
        val events = mutableStateOf(emptyList<CalendarEvent>())
        compose.setContent { MaterialTheme { DayTimeline(selected.value, events.value, {}) } }
        compose.onNodeWithText("08:00").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsNotDisplayed()
        compose.onNodeWithText("00:00").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { events.value = listOf(event("new")) }
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("23:00").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { selected.value = date.plusDays(1) }
        compose.onNodeWithText("08:00").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsNotDisplayed()
    }

    @Test fun overlappingBlocksAreParallelAndSingleBlocksUseFullWidthAndRemainClickable() {
        var clicked: CalendarEvent? = null
        val a = event("A", 540, 660)
        val b = event("B", 540, 600).copy(category = EventCategory.PERSONAL)
        val c = event("C", 660, 720)
        compose.setContent { MaterialTheme { DayTimeline(date, listOf(a, b, c), { clicked = it }) } }
        val first = compose.onNodeWithTag("day_event_A").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("day_event_B").fetchSemanticsNode().boundsInRoot
        val solo = compose.onNodeWithTag("day_event_C").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(first.right <= second.left)
        assertTrue(solo.width > first.width * 1.9f)
        assertEquals(first.height, second.height * 2, 8f)
        compose.onNodeWithTag("day_event_B").performClick()
        compose.runOnIdle { assertEquals(b, clicked) }
    }

    @Test fun dailyPanelWorksInsideTheAppScrollAndKeepsAllDayEventsSeparate() {
        val theme = mutableStateOf(CalendarThemeOption.Warm)
        val a = event("Reunión de equipo", 540, 660)
        val b = event("Clase", 570, 630).copy(category = EventCategory.FITNESS)
        val c = event("Comida", 720, 780).copy(category = EventCategory.COFFEE)
        compose.setContent {
            CompositionLocalProvider(LocalCalendarPalette provides theme.value.palette) {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().background(theme.value.palette.background.last())
                        .verticalScroll(rememberScrollState()).padding(16.dp)) {
                        SummaryPanel(2)
                        Spacer(Modifier.height(14.dp))
                        AgendaPanel(date, "VIERNES, 23 OCTUBRE", listOf(event("Viaje").copy(allDay = true)),
                            listOf(a, b, c), {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Todo el día").assertExists()
        compose.onNodeWithTag("day_event_Viaje").assertDoesNotExist()
        for (option in CalendarThemeOption.values()) {
            compose.runOnIdle { theme.value = option }
            compose.onNodeWithTag("day_timeline").assertExists()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val output = File(context.externalCacheDir ?: context.cacheDir, "day-agenda-${option.name}.png")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

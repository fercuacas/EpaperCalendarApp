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
import com.fernando.epapercalendar.calendar.device.DeviceCalendarIdentity
import com.fernando.epapercalendar.calendar.device.DeviceEventSource
import com.fernando.epapercalendar.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WeekTimelineInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 10, 19)
    private val dates = (0..6).map { monday.plusDays(it.toLong()) }
    private fun event(id: String, start: Int, end: Int) = CalendarEvent(
        id, id, "", monday, startMinutes = start, endMinutes = end, category = EventCategory.WORK)

    @Test fun everyHourIsPresentWithoutFreeGapsAndEachWeekOpensAtEight() {
        val week = mutableStateOf(dates)
        val events = mutableStateOf(emptyMap<LocalDate, List<CalendarEvent>>())
        compose.setContent { MaterialTheme { WeekTimeline(week.value, events.value, {}, {}) } }
        compose.onNodeWithTag("week_hour_8").assertIsDisplayed()
        compose.onNodeWithTag("week_hour_0").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { events.value = mapOf(monday to listOf(event("new", 540, 600))) }
        compose.onNodeWithTag("week_hour_0").assertIsDisplayed()
        compose.onNodeWithTag("week_hour_13").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("week_hour_23").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mañana").assertDoesNotExist()
        compose.onNodeWithText("Tarde").assertDoesNotExist()
        compose.onNodeWithText("Noche").assertDoesNotExist()
        compose.onNodeWithText("Libre", substring = true).assertDoesNotExist()
        compose.runOnIdle { week.value = dates.map { it.plusWeeks(1) } }
        compose.onNodeWithTag("week_hour_8").assertIsDisplayed()
    }

    @Test fun equalStartsShowCountAndStaggeredBlocksRespectMinutesAndDurations() {
        var selected: LocalDate? = null
        var imported: CalendarEvent? = null
        val source = DeviceEventSource(DeviceCalendarIdentity(1, "Prueba", "local"), "Importado", 1, 0)
        val foreign = event("Evento importado", 780, 840).copy(deviceSource = source)
        val events = listOf(event("Reunión larga", 540, 720), event("Otra reunión", 540, 600),
            event("Café corto", 570, 630), foreign)
        compose.setContent { MaterialTheme {
            WeekTimeline(dates, mapOf(monday to events), { imported = it }, { selected = it })
        } }
        compose.onNodeWithTag("week_start_${monday}_540", useUnmergedTree = true).assertTextEquals("2")
        val first = compose.onNodeWithTag("week_event_${monday}_540").fetchSemanticsNode().boundsInRoot
        val later = compose.onNodeWithTag("week_event_${monday}_570").fetchSemanticsNode().boundsInRoot
        assertTrue(first.right <= later.left)
        assertEquals(first.height / 6, later.top - first.top, 2f)
        assertEquals(first.height / 3, later.height, 2f)
        compose.onNodeWithTag("week_event_${monday}_540").performClick()
        compose.runOnIdle { assertEquals(monday, selected) }
        compose.onNodeWithTag("week_event_${monday}_780").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(foreign, imported) }
    }

    @Test fun gridWorksInsideParentScrollAndRendersInAllThemes() {
        val theme = mutableStateOf(CalendarThemeOption.Warm)
        val events = listOf(event("Reunión equipo", 540, 720), event("Clase inglés", 570, 660)
            .copy(category = EventCategory.PERSONAL), event("Café amigos", 540, 600)
            .copy(category = EventCategory.COFFEE))
        compose.setContent {
            CompositionLocalProvider(LocalCalendarPalette provides theme.value.palette) {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().background(theme.value.palette.background.last())
                        .verticalScroll(rememberScrollState()).padding(16.dp)) {
                        WeekDaysHeader(dates, monday, {})
                        WeekTimeline(dates, mapOf(monday to events), {}, {})
                    }
                }
            }
        }
        for (option in CalendarThemeOption.values()) {
            compose.runOnIdle { theme.value = option }
            compose.onNodeWithTag("week_start_${monday}_540", useUnmergedTree = true).assertTextEquals("2")
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val output = File(context.externalCacheDir ?: context.cacheDir, "week-timeline-${option.name}.png")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

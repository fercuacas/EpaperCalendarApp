package com.fernando.epapercalendar

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.fernando.epapercalendar.calendar.*
import com.fernando.epapercalendar.ui.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun state() = CalendarState(object : CalendarStore {
        override fun loadEvents() = emptyList<CalendarEvent>()
        override fun saveEvents(events: List<CalendarEvent>) = error("No writes in UI tests")
        override fun loadSection() = AppSection.AGENDA
        override fun saveSection(section: AppSection) = error("No writes in UI tests")
        override fun loadCategoryFilter(): EventCategory? = null
        override fun saveCategoryFilter(category: EventCategory?) = error("No writes in UI tests")
        override fun loadDateFilter() = DateFilter.ALL
        override fun saveDateFilter(filter: DateFilter) = error("No writes in UI tests")
    }, initialEvents = emptyList(), persistChanges = false)

    @Test fun filtersAndSearchOnlyAppearInWeekAndMonth() {
        val state = state()
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { CalendarFilters(state, true) {} }
        } }
        for (section in listOf(AppSection.AGENDA, AppSection.WEEK, AppSection.TASKS, AppSection.CALENDAR, AppSection.AGENDA)) {
            compose.runOnIdle { state.selectSection(section) }
            if (section == AppSection.WEEK || section == AppSection.CALENDAR) {
                compose.onNodeWithTag("calendar_filters").assertIsDisplayed()
                compose.onNodeWithText("Buscar evento").assertIsDisplayed()
                compose.onNodeWithText("Categorías").assertIsDisplayed()
            } else {
                compose.onNodeWithTag("calendar_filters").assertDoesNotExist()
                compose.onNodeWithText("Buscar evento").assertDoesNotExist()
            }
        }
    }

    @Test fun weekAndMonthArrowsChangeTheVisiblePeriodInBothDirections() {
        val state = state()
        state.selectSection(AppSection.WEEK)
        state.selectDate(LocalDate.of(2026,12,30))
        val month = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (month.value) MonthSection(state, {}, {}) else WeekSection(state, {}, {})
            }
        } }
        compose.onNodeWithContentDescription("Semana siguiente").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2027,1,6), state.selectedDate) }
        compose.onNodeWithContentDescription("Semana anterior").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026,12,30), state.selectedDate) }
        screenshot("week-navigation.png")
        compose.runOnIdle {
            state.selectDate(LocalDate.of(2027,1,31))
            state.selectSection(AppSection.CALENDAR)
            month.value = true
        }
        compose.onNodeWithContentDescription("Mes siguiente").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2027,2,28), state.selectedDate) }
        compose.onNodeWithText("Febrero 2027").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2027,1,28), state.selectedDate) }
        screenshot("month-navigation.png")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir ?: context.cacheDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}

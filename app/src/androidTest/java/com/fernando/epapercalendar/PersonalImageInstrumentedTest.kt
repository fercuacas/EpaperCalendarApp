package com.fernando.epapercalendar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.fernando.epapercalendar.personal.PersonalContentCatalog
import com.fernando.epapercalendar.ui.*
import com.fernando.epapercalendar.calendar.*
import java.time.LocalDate
import java.util.Base64
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject

class PersonalImageInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dailyPngAppearsAndTutorialDoesNotShowPersonalPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open(PersonalContentCatalog.ASSET_PATH).bufferedReader().use {
            PersonalContentCatalog.fromJson(it.readText())
        }
        val date = LocalDate.of(2026,10,26)
        val selected = catalog.forDate(date)
        val bytes = Base64.getDecoder().decode(selected.image!!.previewPng)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        assertTrue(bitmap.width in 1..240 && bitmap.height in 1..240)
        val enabled = mutableStateOf(true)
        val description = "Foto del e-paper para $date: ${selected.image.name}"
        compose.setContent { MaterialTheme {
            PersonalDailyImage(Modifier.size(124.dp), enabled.value, date)
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir ?: context.cacheDir, "personal-daily-image.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { enabled.value = false }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Imagen de ejemplo del tutorial").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
    }

    @Test fun selectedAgendaDateChangesBothPhraseAndPhotoAndNavigatingBackRestoresThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalogText = context.assets.open(PersonalContentCatalog.ASSET_PATH).bufferedReader().use { it.readText() }
        val catalog = PersonalContentCatalog.fromJson(catalogText)
        val first = LocalDate.of(2026,10,31)
        val firstContent = catalog.forDate(first)
        val next = (1L..120L).map(first::plusDays).first {
            val content = catalog.forDate(it)
            content.phrase != firstContent.phrase && content.image?.name != firstContent.image?.name
        }
        val state = CalendarState(repository = object : CalendarStore {
            override fun loadEvents() = emptyList<CalendarEvent>()
            override fun saveEvents(events: List<CalendarEvent>) = error("Test must never persist")
            override fun loadSection() = AppSection.AGENDA
            override fun saveSection(section: AppSection) = error("Test must never persist")
            override fun loadCategoryFilter(): EventCategory? = null
            override fun saveCategoryFilter(category: EventCategory?) = error("Test must never persist")
            override fun loadDateFilter() = DateFilter.ALL
            override fun saveDateFilter(filter: DateFilter) = error("Test must never persist")
        }, persistChanges = false, initialEvents = emptyList())
        state.selectDate(first)
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                AgendaSection(state, {}, {}, {})
            }
        } }
        fun awaitDate(date: LocalDate) {
            val content = catalog.forDate(date)
            val description = "Foto del e-paper para $date: ${content.image?.name}"
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(content.phrase!!).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("personal_daily_phrase").assertTextEquals(content.phrase!!)
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        awaitDate(first)
        File(context.externalCacheDir ?: context.cacheDir, "summary-selected-date.png").outputStream().use {
            compose.onNodeWithTag("agenda_summary").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { state.selectDate(next) }
        awaitDate(next)
        compose.onNodeWithContentDescription("Foto del e-paper para $first: ${firstContent.image?.name}").assertDoesNotExist()
        compose.runOnIdle { state.selectDate(first) }
        awaitDate(first)
        compose.runOnIdle { for (offset in 1L..25L) state.selectDate(first.plusDays(offset)) }
        awaitDate(first.plusDays(25))
        val source = JSONObject(catalogText)
        for (key in listOf("specialPhrases", "specialImages")) {
            val entries = source.getJSONArray(key)
            if (entries.length() > 0) {
                val special = LocalDate.parse("2028-${entries.getJSONObject(0).getString("date")}")
                compose.runOnIdle { state.selectDate(special) }
                awaitDate(special)
            }
        }
    }

    @Test fun roundedSummaryKeepsLongQuoteAndSquarePhotoInsideCardAcrossThemesAndLargeFonts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedTheme = mutableStateOf(CalendarThemeOption.Warm)
        val fontScale = mutableStateOf(1f)
        val longQuote = "Este es un mensaje largo para comprobar que toda la frase se muestra completa, " +
            "sin recortes ni solapamientos, incluso con una pantalla estrecha y letra grande."
        val image = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
        val day = PersonalDayUi(LocalDate.of(2026,10,31), longQuote, "test.epd", image)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalCalendarPalette provides selectedTheme.value.palette,
                LocalDensity provides Density(density, fontScale.value)) {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().background(selectedTheme.value.palette.background.last())
                        .verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Box(Modifier.width(320.dp)) { SummaryPanelContent(2, day) }
                    }
                }
            }
        }
        for (theme in CalendarThemeOption.values()) for (scale in listOf(1f, 1.6f)) {
            compose.runOnIdle { selectedTheme.value = theme; fontScale.value = scale }
            val card = compose.onNodeWithTag("agenda_summary").getUnclippedBoundsInRoot()
            val photo = compose.onNodeWithTag("personal_daily_image").getUnclippedBoundsInRoot()
            val quote = compose.onNodeWithTag("personal_daily_phrase").getUnclippedBoundsInRoot()
            val counter = compose.onNodeWithText("2 tareas pendientes").getUnclippedBoundsInRoot()
            assertEquals((photo.right - photo.left).value, (photo.bottom - photo.top).value, 1f)
            assertTrue(photo.left > card.left && photo.right < card.right)
            assertTrue(photo.top > card.top && photo.bottom < card.bottom)
            assertTrue(counter.right < photo.left && counter.top > quote.bottom)
            assertTrue(quote.left > card.left && quote.right < photo.left && counter.bottom < card.bottom)
            compose.onNodeWithTag("personal_daily_phrase").assertTextEquals(longQuote)
            for (greeting in listOf("Buenos días", "Buenas tardes", "Buenas noches")) {
                compose.onNodeWithText(greeting).assertDoesNotExist()
            }
            val screenshot = compose.onNodeWithTag("agenda_summary").captureToImage().asAndroidBitmap()
            File(context.externalCacheDir ?: context.cacheDir, "summary-${theme.name}-$scale.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}

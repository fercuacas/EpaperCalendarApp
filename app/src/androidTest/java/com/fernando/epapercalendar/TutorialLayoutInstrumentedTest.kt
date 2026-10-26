package com.fernando.epapercalendar

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fernando.epapercalendar.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TutorialLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allStepsKeepCardAndControlsInsideSafeInsets() {
        val current = mutableStateOf(0)
        var px = 1f
        var root = Rect.Zero
        compose.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                px = density.density
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    root = Rect(0f, 0f, with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
                    val step = tutorialSteps[current.value]
                    val target = when (step.focusTarget) {
                        TutorialFocusTarget.WHOLE_APP -> root
                        TutorialFocusTarget.TOP_MENU, TutorialFocusTarget.SEARCH_BUTTON,
                        TutorialFocusTarget.FILTERS, TutorialFocusTarget.SEARCH_FIELD -> Rect(32f * px, 80f * px, 180f * px, 135f * px)
                        else -> Rect(40f * px, root.bottom - 180f * px, root.right - 40f * px, root.bottom - 80f * px)
                    }
                    TutorialOverlay(step, current.value, tutorialSteps.size, mapOf(step.focusTarget to target),
                        {}, {}, {}, {}, WindowInsets(left = 10.dp, top = 48.dp, right = 10.dp, bottom = 28.dp))
                }
            }
        }
        tutorialSteps.indices.forEach { index ->
            compose.runOnIdle { current.value = index }
            compose.waitForIdle()
            val card = compose.onNodeWithTag("tutorial_callout").fetchSemanticsNode().boundsInRoot
            assertTrue("step $index top", card.top >= 60f * px - 1)
            assertTrue("step $index bottom", card.bottom <= root.bottom - 40f * px + 1)
            assertTrue(card.left >= 26f * px - 1)
            assertTrue(card.right <= root.right - 26f * px + 1)
            compose.onNodeWithContentDescription("Salir del tutorial").assertIsDisplayed()
            if (index > 0) compose.onNodeWithTag("tutorial_previous").assertIsDisplayed()
            if (!tutorialSteps[index].requiresTargetTap) compose.onNodeWithTag("tutorial_next").assertIsDisplayed()
            if (tutorialSteps[index].preview != null) {
                val preview = compose.onNodeWithTag("tutorial_preview").fetchSemanticsNode().boundsInRoot
                assertTrue(card.top >= preview.bottom + 19f * px || card.bottom <= preview.top - 19f * px)
                saveScreenshot("preview-$index")
            }
        }
    }

    @Test fun bottomTargetRemainsClickableAndNavigationAndExitStillWork() {
        var targetClicks = 0
        var previousClicks = 0
        var exits = 0
        var target = Rect.Zero
        compose.setContent {
            MaterialTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    target = with(density) { Rect(100.dp.toPx(), (maxHeight - 120.dp).toPx(), 200.dp.toPx(), (maxHeight - 60.dp).toPx()) }
                    TutorialOverlay(tutorialSteps[3], 3, tutorialSteps.size, mapOf(TutorialFocusTarget.ROBOFER to target),
                        { targetClicks++ }, { previousClicks++ }, {}, { exits++ },
                        WindowInsets(top = 48.dp, bottom = 28.dp))
                }
            }
        }
        val card = compose.onNodeWithTag("tutorial_callout").fetchSemanticsNode().boundsInRoot
        assertTrue(card.bottom < target.top)
        compose.onNodeWithTag("tutorial_target").performTouchInput { click() }
        compose.onNodeWithTag("tutorial_previous").performClick()
        compose.onNodeWithContentDescription("Salir del tutorial").performClick()
        compose.runOnIdle { assertEquals(1, targetClicks); assertEquals(1, previousClicks); assertEquals(1, exits) }
        saveScreenshot("bottom-target")
    }

    @Test fun smallWindowAndLargeFontKeepButtonsVisibleAndLongTextScrollable() {
        var nextClicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(320.dp, 480.dp)) {
                        TutorialOverlay(tutorialSteps[6].copy(body = (tutorialSteps[6].body + " ").repeat(20) + "FIN DEL TEXTO"),
                            6, tutorialSteps.size, emptyMap(), {}, {}, { nextClicks++ }, {},
                            WindowInsets(top = 48.dp, bottom = 28.dp))
                    }
                }
            }
        }
        compose.onNodeWithTag("tutorial_next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, nextClicks) }
        compose.onNodeWithTag("tutorial_body").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        assertTrue(compose.onNodeWithTag("tutorial_body").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithTag("tutorial_next").assertIsDisplayed()
        val previous = compose.onNodeWithTag("tutorial_previous").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("tutorial_next").fetchSemanticsNode().boundsInRoot
        assertTrue(previous.bottom <= next.top)
        compose.onNodeWithContentDescription("Salir del tutorial").assertIsDisplayed()
        saveScreenshot("small-large-font")
    }

    @Test fun welcomeAndAdvancedStepsAreCleanBelowTheStatusBar() {
        val index = mutableStateOf(0)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(CalendarThemeOption.Warm.palette.background.first())) {
                    TutorialOverlay(tutorialSteps[index.value], index.value, tutorialSteps.size, emptyMap(),
                        {}, {}, {}, {}, WindowInsets(top = 48.dp, bottom = 28.dp))
                }
            }
        }
        saveScreenshot("welcome")
        compose.runOnIdle { index.value = 6 }
        compose.onNodeWithTag("tutorial_next").assertIsDisplayed()
        saveScreenshot("advanced")
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.externalCacheDir ?: context.cacheDir, "tutorial-$name.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

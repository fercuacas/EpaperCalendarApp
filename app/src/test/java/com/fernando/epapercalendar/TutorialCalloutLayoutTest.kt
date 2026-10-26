package com.fernando.epapercalendar

import androidx.compose.ui.geometry.Rect
import com.fernando.epapercalendar.ui.tutorialCalloutRegion
import org.junit.Assert.*
import org.junit.Test

class TutorialCalloutLayoutTest {
    private val safe = Rect(16f, 60f, 344f, 760f)

    @Test fun bottomTargetLeavesCalloutAboveWithoutCrossingStatusBar() {
        val target = Rect(140f, 660f, 220f, 740f)
        val area = tutorialCalloutRegion(safe, target, 20f, 220f, false)
        assertEquals(safe.top, area.top)
        assertEquals(640f, area.bottom)
        assertFalse(area.alignBottom)
    }

    @Test fun topTargetPlacesCalloutBelowWithGap() {
        val target = Rect(16f, 70f, 90f, 120f)
        val area = tutorialCalloutRegion(safe, target, 20f, 220f, false)
        assertEquals(140f, area.top)
        assertEquals(safe.bottom, area.bottom)
        assertTrue(area.alignBottom)
    }

    @Test fun choosesTheLargerFreeRegionForMiddleTargets() {
        val area = tutorialCalloutRegion(safe, Rect(16f, 420f, 344f, 480f), 20f, 220f, true)
        assertFalse(area.alignBottom)
        assertEquals(400f, area.bottom)
    }

    @Test fun absentOrWholeScreenTargetsUseSafeAreaAndFallback() {
        assertEquals(60f, tutorialCalloutRegion(safe, null, 20f, 220f, false).top)
        val area = tutorialCalloutRegion(safe, Rect(0f, 0f, 360f, 800f), 20f, 220f, true)
        assertEquals(safe.top, area.top)
        assertEquals(safe.bottom, area.bottom)
        assertTrue(area.alignBottom)
    }

    @Test fun offscreenTargetsCannotPushCalloutOutsideSafeArea() {
        listOf(Rect(0f, -100f, 300f, -20f), Rect(0f, 900f, 300f, 980f)).forEach {
            val area = tutorialCalloutRegion(safe, it, 20f, 220f, false)
            assertEquals(safe.top, area.top)
            assertEquals(safe.bottom, area.bottom)
        }
    }

    @Test fun smallWindowsUseScrollableCardInsteadOfNegativeHeight() {
        val small = Rect(16f, 60f, 304f, 290f)
        val area = tutorialCalloutRegion(small, Rect(20f, 150f, 200f, 200f), 20f, 220f, false)
        assertEquals(small.top, area.top)
        assertEquals(small.bottom, area.bottom)
        assertTrue(area.bottom > area.top)
    }
}

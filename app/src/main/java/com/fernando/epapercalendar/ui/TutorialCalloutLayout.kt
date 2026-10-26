package com.fernando.epapercalendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

internal data class TutorialCalloutRegion(val top: Float, val bottom: Float, val alignBottom: Boolean)

/** All coordinates share the overlay root, not the padded Scaffold's coordinate space. */
internal fun tutorialCalloutRegion(
    safeArea: Rect,
    target: Rect?,
    gap: Float,
    minimumHeight: Float,
    preferBottom: Boolean
): TutorialCalloutRegion {
    if (target != null && target.bottom > safeArea.top && target.top < safeArea.bottom) {
        val aboveBottom = (target.top - gap).coerceIn(safeArea.top, safeArea.bottom)
        val belowTop = (target.bottom + gap).coerceIn(safeArea.top, safeArea.bottom)
        val above = aboveBottom - safeArea.top
        val below = safeArea.bottom - belowTop
        if (maxOf(above, below) >= minimumHeight) {
            return if (below > above || (below == above && preferBottom)) {
                TutorialCalloutRegion(belowTop, safeArea.bottom, true)
            } else {
                TutorialCalloutRegion(safeArea.top, aboveBottom, false)
            }
        }
    }
    // Whole-screen highlights or tiny windows cannot leave a separate callout region.
    return TutorialCalloutRegion(safeArea.top, safeArea.bottom, preferBottom)
}

@Composable
internal fun TutorialCalloutContainer(
    safeAreaInsets: Rect,
    target: Rect?,
    gap: Float,
    minimumHeight: Float,
    maximumHeight: Float,
    preferBottom: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val safeArea = Rect(
            safeAreaInsets.left.coerceAtMost((constraints.maxWidth - 1).coerceAtLeast(0).toFloat()),
            safeAreaInsets.top.coerceAtMost((constraints.maxHeight - 1).coerceAtLeast(0).toFloat()),
            (constraints.maxWidth - safeAreaInsets.right).coerceAtLeast(safeAreaInsets.left + 1),
            (constraints.maxHeight - safeAreaInsets.bottom).coerceAtLeast(safeAreaInsets.top + 1)
        )
        val region = tutorialCalloutRegion(safeArea, target, gap, minimumHeight, preferBottom)
        val width = safeArea.width.roundToInt().coerceAtLeast(1)
        val height = minOf(region.bottom - region.top, maximumHeight).roundToInt().coerceAtLeast(1)
        val card = measurables.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = if (region.alignBottom) region.bottom - card.height else region.top
            card.placeRelative(safeArea.left.roundToInt(), top.roundToInt())
        }
    }
}

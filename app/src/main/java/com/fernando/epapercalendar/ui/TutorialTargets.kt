package com.fernando.epapercalendar.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

internal val LocalTutorialTargetRegistry = compositionLocalOf<SnapshotStateMap<TutorialFocusTarget, Rect>?> { null }

internal fun Modifier.tutorialTarget(target: TutorialFocusTarget): Modifier = composed {
    val registry = LocalTutorialTargetRegistry.current
    if (registry == null) {
        this
    } else {
        onGloballyPositioned { coordinates ->
            registry[target] = coordinates.boundsInRoot()
        }
    }
}

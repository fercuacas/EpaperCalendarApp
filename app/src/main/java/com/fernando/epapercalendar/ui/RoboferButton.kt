package com.fernando.epapercalendar.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.fernando.epapercalendar.calendar.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun BottomCalendarBar(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    roboferState: RoboferState,
    onRoboferClick: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = palette.accent,
        selectedTextColor = palette.accent,
        indicatorColor = palette.accentSoft.copy(alpha = 0.72f),
        unselectedIconColor = palette.iconMuted,
        unselectedTextColor = palette.textMuted.copy(alpha = 0.72f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp)),
            containerColor = palette.surface.copy(alpha = 0.9f),
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = selectedSection == AppSection.AGENDA,
                onClick = { onSectionSelected(AppSection.AGENDA) },
                modifier = Modifier.tutorialTarget(TutorialFocusTarget.AGENDA_TAB),
                icon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                label = { Text("Agenda") },
                colors = navColors
            )
            NavigationBarItem(
                selected = selectedSection == AppSection.WEEK,
                onClick = { onSectionSelected(AppSection.WEEK) },
                modifier = Modifier.tutorialTarget(TutorialFocusTarget.WEEK_TAB),
                icon = { Icon(Icons.Rounded.ViewWeek, contentDescription = null) },
                label = { Text("Semana") },
                colors = navColors
            )
            Spacer(modifier = Modifier.width(88.dp))
            NavigationBarItem(
                selected = selectedSection == AppSection.TASKS,
                onClick = { onSectionSelected(AppSection.TASKS) },
                modifier = Modifier.tutorialTarget(TutorialFocusTarget.TASKS_TAB),
                icon = { Icon(Icons.Rounded.CheckBox, contentDescription = null) },
                label = { Text("Tareas") },
                colors = navColors
            )
            NavigationBarItem(
                selected = selectedSection == AppSection.CALENDAR,
                onClick = { onSectionSelected(AppSection.CALENDAR) },
                modifier = Modifier.tutorialTarget(TutorialFocusTarget.MONTH_TAB),
                icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                label = { Text("Mes") },
                colors = navColors
            )
        }
        RoboferButton(
            state = roboferState,
            onClick = onRoboferClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 0.dp)
                .size(width = 88.dp, height = 82.dp)
                .tutorialTarget(TutorialFocusTarget.ROBOFER)
        )
    }
}

@Composable
internal fun RoboferButton(
    state: RoboferState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    val transition = rememberInfiniteTransition(label = "robofer-motion")
    val alivePulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720),
            repeatMode = RepeatMode.Reverse
        ),
        label = "robofer-eyes"
    )
    val eyeLook by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "robofer-look"
    )
    val wakeProgress by animateFloatAsState(
        targetValue = if (state == RoboferState.Resting) 0f else 1f,
        animationSpec = tween(durationMillis = 360),
        label = "robofer-wake"
    )
    val successProgress by animateFloatAsState(
        targetValue = if (state == RoboferState.Success) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "robofer-success"
    )
    val targetScale = when (state) {
        RoboferState.Active -> 1.05f
        RoboferState.Success -> 1.06f
        RoboferState.Resting -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 220),
        label = "robofer-scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoboferBase(wakeProgress, palette)
            drawRoboferMotion(alivePulse, wakeProgress * (1f - successProgress), palette)
            drawRoboferPlus(
                morphProgress = wakeProgress,
                alpha = (1f - wakeProgress).coerceIn(0f, 1f)
            )
            drawRoboferEyes(
                pulse = alivePulse,
                look = eyeLook,
                alpha = (wakeProgress * (1f - successProgress)).coerceIn(0f, 1f)
            )
            drawRoboferSuccess(successProgress, palette)
            drawRoboferStars(successProgress, palette)
        }
    }
}

internal fun DrawScope.drawRoboferBase(wakeProgress: Float, palette: CalendarPalette) {
    val w = size.width
    val h = size.height
    val faceColor = palette.surfaceStrong
    val edgeColor = palette.softLine
    val screenColor = Color(0xFF101116)
    val frameCenter = Offset(w * 0.5f, h * 0.535f)
    val frameRadius = size.minDimension * 0.47f

    drawCircle(
        color = palette.accentSoft.copy(alpha = 0.52f + wakeProgress * 0.08f),
        radius = frameRadius,
        center = frameCenter
    )

    val leftEar = Path().apply {
        moveTo(w * 0.22f, h * 0.32f)
        lineTo(w * 0.34f, h * 0.105f)
        lineTo(w * 0.49f, h * 0.315f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(w * 0.51f, h * 0.315f)
        lineTo(w * 0.66f, h * 0.105f)
        lineTo(w * 0.78f, h * 0.32f)
        close()
    }
    drawPath(leftEar, edgeColor.copy(alpha = 0.34f))
    drawPath(rightEar, edgeColor.copy(alpha = 0.34f))
    drawPath(leftEar, faceColor)
    drawPath(rightEar, faceColor)
    drawPath(
        Path().apply {
            moveTo(w * 0.3f, h * 0.28f)
            lineTo(w * 0.345f, h * 0.18f)
            lineTo(w * 0.41f, h * 0.285f)
            close()
        },
        palette.accentSoft.copy(alpha = 0.7f)
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.59f, h * 0.285f)
            lineTo(w * 0.655f, h * 0.18f)
            lineTo(w * 0.7f, h * 0.28f)
            close()
        },
        palette.accentSoft.copy(alpha = 0.7f)
    )

    drawRoundRect(
        color = edgeColor.copy(alpha = 0.38f),
        topLeft = Offset(w * 0.15f, h * 0.285f),
        size = Size(w * 0.7f, h * 0.51f),
        cornerRadius = CornerRadius(28f, 28f)
    )
    drawRoundRect(
        color = faceColor,
        topLeft = Offset(w * 0.17f, h * 0.255f),
        size = Size(w * 0.66f, h * 0.53f),
        cornerRadius = CornerRadius(30f, 30f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = Offset(w * 0.26f, h * 0.29f),
        size = Size(w * 0.16f, h * 0.04f),
        cornerRadius = CornerRadius(20f, 20f)
    )
    drawRoundRect(
        color = palette.accentSecondary.copy(alpha = 0.78f),
        topLeft = Offset(w * 0.125f, h * 0.52f),
        size = Size(w * 0.04f, h * 0.115f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = palette.accentSecondary.copy(alpha = 0.78f),
        topLeft = Offset(w * 0.835f, h * 0.52f),
        size = Size(w * 0.04f, h * 0.115f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = screenColor,
        topLeft = Offset(w * 0.2f, h * 0.335f),
        size = Size(w * 0.6f, h * 0.38f),
        cornerRadius = CornerRadius(22f, 22f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f),
        topLeft = Offset(w * 0.25f, h * 0.365f),
        size = Size(w * 0.2f, h * 0.045f),
        cornerRadius = CornerRadius(12f, 12f)
    )
}

internal fun DrawScope.drawRoboferPlus(morphProgress: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    val color = Color(0xFFA8ECFF).copy(alpha = alpha)
    val center = Offset(w * 0.5f, h * 0.525f)
    val halfVertical = w * (0.095f - morphProgress * 0.05f)
    val halfHorizontal = w * (0.12f - morphProgress * 0.08f)
    drawLine(
        color = color,
        start = Offset(center.x, center.y - halfVertical),
        end = Offset(center.x, center.y + halfVertical),
        strokeWidth = w * (0.056f - morphProgress * 0.018f),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x - halfHorizontal, center.y),
        end = Offset(center.x + halfHorizontal, center.y),
        strokeWidth = w * (0.056f - morphProgress * 0.018f),
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawRoboferEyes(pulse: Float, look: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    val eyeColor = Color(0xFF55D9FF).copy(alpha = alpha)
    val glowColor = Color(0xFF55D9FF).copy(alpha = alpha * 0.24f)
    val eyeHeight = h * (0.074f + pulse * 0.02f)
    val xShift = w * 0.018f * look
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(w * 0.335f + xShift, h * 0.455f),
        size = Size(w * 0.13f, eyeHeight),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawRoundRect(
        color = eyeColor,
        topLeft = Offset(w * 0.345f + xShift, h * 0.462f),
        size = Size(w * 0.11f, eyeHeight * 0.72f),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(w * 0.535f + xShift, h * 0.455f),
        size = Size(w * 0.13f, eyeHeight),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawRoundRect(
        color = eyeColor,
        topLeft = Offset(w * 0.545f + xShift, h * 0.462f),
        size = Size(w * 0.11f, eyeHeight * 0.72f),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawLine(
        color = eyeColor.copy(alpha = alpha * 0.64f),
        start = Offset(w * 0.43f + xShift * 0.4f, h * 0.595f),
        end = Offset(w * 0.57f + xShift * 0.4f, h * 0.595f),
        strokeWidth = w * 0.025f,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawRoboferMotion(pulse: Float, alpha: Float, palette: CalendarPalette) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    val color = palette.sun.copy(alpha = alpha * (0.28f + pulse * 0.5f))
    drawLine(color, Offset(w * 0.16f, h * 0.2f), Offset(w * 0.08f, h * 0.12f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.83f, h * 0.19f), Offset(w * 0.92f, h * 0.11f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.1f, h * 0.74f), Offset(w * 0.03f, h * 0.82f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.9f, h * 0.74f), Offset(w * 0.97f, h * 0.82f), strokeWidth = 4f, cap = StrokeCap.Round)
}

internal fun DrawScope.drawRoboferSuccess(progress: Float, palette: CalendarPalette) {
    if (progress <= 0.01f) return
    val w = size.width
    val h = size.height
    val color = palette.success.copy(alpha = progress)
    val start = Offset(w * 0.36f, h * 0.525f)
    val corner = Offset(w * 0.47f, h * 0.61f)
    val end = Offset(w * 0.66f, h * 0.44f)
    val firstProgress = (progress * 2f).coerceIn(0f, 1f)
    val secondProgress = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
    drawLine(
        color = color,
        start = start,
        end = Offset(
            x = start.x + (corner.x - start.x) * firstProgress,
            y = start.y + (corner.y - start.y) * firstProgress
        ),
        strokeWidth = w * 0.07f,
        cap = StrokeCap.Round
    )
    if (secondProgress > 0f) {
        drawLine(
            color = color,
            start = corner,
            end = Offset(
                x = corner.x + (end.x - corner.x) * secondProgress,
                y = corner.y + (end.y - corner.y) * secondProgress
            ),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawRoboferStars(progress: Float, palette: CalendarPalette) {
    if (progress <= 0.01f) return
    val w = size.width
    val h = size.height
    val color = palette.sun.copy(alpha = progress)
    fun star(center: Offset, radius: Float) {
        val scaledRadius = radius * progress
        drawLine(color, Offset(center.x - scaledRadius, center.y), Offset(center.x + scaledRadius, center.y), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - scaledRadius), Offset(center.x, center.y + scaledRadius), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    star(Offset(w * 0.18f, h * 0.29f), w * 0.045f)
    star(Offset(w * 0.82f, h * 0.29f), w * 0.04f)
    star(Offset(w * 0.2f, h * 0.78f), w * 0.035f)
    star(Offset(w * 0.8f, h * 0.77f), w * 0.035f)
}

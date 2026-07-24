package com.fernando.epapercalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fernando.epapercalendar.calendar.AppSection
import kotlin.math.roundToInt

internal enum class TutorialDemoAction {
    EVENT_CREATED,
    TASK_CREATED,
    SEARCH_READY,
    WEEK_READY,
    MONTH_READY,
    EDIT_READY,
    MENU_READY
}

internal enum class TutorialFocusTarget {
    WHOLE_APP,
    TOP_MENU,
    SEARCH_BUTTON,
    ROBOFER,
    AGENDA_LIST,
    ADD_PANEL,
    CREATE_EVENT_BUTTON,
    AGENDA_TAB,
    WEEK_TAB,
    TASKS_TAB,
    SEARCH_FIELD,
    WEEK_VIEW,
    MONTH_TAB,
    MONTH_VIEW,
    EDIT_PANEL
}

internal data class TutorialStep(
    val title: String,
    val body: String,
    val section: AppSection = AppSection.AGENDA,
    val openCreatePanel: Boolean = false,
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val demoAction: TutorialDemoAction? = null,
    val focusTarget: TutorialFocusTarget = TutorialFocusTarget.WHOLE_APP,
    val requiresTargetTap: Boolean = true
)

internal val tutorialSteps = listOf(
    TutorialStep(
        title = "Bienvenido a Robofer",
        body = "Este recorrido es temporal. La app te irá dejando tocar solo la zona marcada para aprender el flujo sin cambiar tus datos reales.",
        focusTarget = TutorialFocusTarget.WHOLE_APP,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Toca a Robofer",
        body = "Robofer es el botón central para crear eventos. Toca la zona resaltada.",
        focusTarget = TutorialFocusTarget.ROBOFER
    ),
    TutorialStep(
        title = "Panel rápido",
        body = "Este panel concentra lo esencial: título, fecha, hora y categoría. Toca el panel para continuar.",
        openCreatePanel = true,
        focusTarget = TutorialFocusTarget.ADD_PANEL
    ),
    TutorialStep(
        title = "Crea el evento demo",
        body = "Toca Crear evento. Se guardará solo dentro del tutorial.",
        openCreatePanel = true,
        focusTarget = TutorialFocusTarget.CREATE_EVENT_BUTTON
    ),
    TutorialStep(
        title = "Mirá la agenda",
        body = "El evento aparece en la agenda temporal. Toca la lista resaltada para seguir.",
        demoAction = TutorialDemoAction.EVENT_CREATED,
        focusTarget = TutorialFocusTarget.AGENDA_LIST
    ),
    TutorialStep(
        title = "Tareas",
        body = "Toca Tareas para ver una tarea demo separada del calendario principal.",
        demoAction = TutorialDemoAction.TASK_CREATED,
        focusTarget = TutorialFocusTarget.TASKS_TAB
    ),
    TutorialStep(
        title = "Búsqueda",
        body = "Toca la lupa para abrir la búsqueda.",
        demoAction = TutorialDemoAction.SEARCH_READY,
        focusTarget = TutorialFocusTarget.SEARCH_BUTTON
    ),
    TutorialStep(
        title = "Filtra por gym",
        body = "Toca el campo. El tutorial escribirá gym y reducirá la lista.",
        showSearch = true,
        searchQuery = "",
        demoAction = TutorialDemoAction.SEARCH_READY,
        focusTarget = TutorialFocusTarget.SEARCH_FIELD
    ),
    TutorialStep(
        title = "Resultado filtrado",
        body = "La lista queda filtrada. Toca la agenda para continuar.",
        showSearch = true,
        searchQuery = "gym",
        demoAction = TutorialDemoAction.SEARCH_READY,
        focusTarget = TutorialFocusTarget.AGENDA_LIST
    ),
    TutorialStep(
        title = "Vista semanal",
        body = "Toca Semana para ver eventos por días y horas.",
        demoAction = TutorialDemoAction.WEEK_READY,
        focusTarget = TutorialFocusTarget.WEEK_TAB
    ),
    TutorialStep(
        title = "Semana",
        body = "Aquí ves contexto por horas y eventos largos arriba. Toca la vista resaltada.",
        section = AppSection.WEEK,
        demoAction = TutorialDemoAction.WEEK_READY,
        focusTarget = TutorialFocusTarget.WEEK_VIEW
    ),
    TutorialStep(
        title = "Vista mes",
        body = "Toca Mes para ver el resumen mensual.",
        section = AppSection.WEEK,
        demoAction = TutorialDemoAction.MONTH_READY,
        focusTarget = TutorialFocusTarget.MONTH_TAB
    ),
    TutorialStep(
        title = "Mes",
        body = "El mes evita saturarse y muestra rangos largos como resumen. Toca la zona marcada.",
        section = AppSection.CALENDAR,
        demoAction = TutorialDemoAction.MONTH_READY,
        focusTarget = TutorialFocusTarget.MONTH_VIEW
    ),
    TutorialStep(
        title = "Editar repetición",
        body = "Este evento recurrente demo muestra que editar una repetición tiene opciones claras.",
        openCreatePanel = true,
        demoAction = TutorialDemoAction.EDIT_READY,
        focusTarget = TutorialFocusTarget.EDIT_PANEL
    ),
    TutorialStep(
        title = "Menú superior",
        body = "Toca las tres barras. Desde ahi puedes abrir tutorial, temas y e-paper.",
        demoAction = TutorialDemoAction.MENU_READY,
        focusTarget = TutorialFocusTarget.TOP_MENU
    ),
    TutorialStep(
        title = "Tutorial completado",
        body = "Listo. Al terminar se descarta la simulación y vuelves a tu calendario real.",
        focusTarget = TutorialFocusTarget.WHOLE_APP,
        requiresTargetTap = false
    )
)

@Composable
internal fun WelcomeDialog(
    onStartTutorial: () -> Unit,
    onEnterApp: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    AlertDialog(
        onDismissRequest = onEnterApp,
        shape = RoundedCornerShape(8.dp),
        containerColor = palette.surface,
        title = {
            Column {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Bienvenido a Epaper Calendar",
                    color = palette.text,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Text(
                text = "Robofer puede enseñarte la app con un tutorial temporal. No tocará tus eventos, tareas ni preferencias reales.",
                color = palette.textMuted,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Button(
                onClick = onStartTutorial,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ver tutorial")
            }
        },
        dismissButton = {
            TextButton(onClick = onEnterApp) {
                Text("Entrar", color = palette.textMuted, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun TutorialOverlay(
    step: TutorialStep,
    stepIndex: Int,
    totalSteps: Int,
    targets: Map<TutorialFocusTarget, Rect>,
    onTargetClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val fallback = step.focusTarget.fallback()
    val targetBounds = targets[step.focusTarget]

    Box(modifier = Modifier.fillMaxSize()) {
        TutorialSpotlight(fallback = fallback, targetBounds = targetBounds)
        TutorialInputBlocker(
            targetBounds = targetBounds,
            fallback = fallback,
            targetEnabled = step.requiresTargetTap,
            onTargetClick = onTargetClick
        )
        TutorialCalloutCard(
            step = step,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            targetBounds = targetBounds,
            fallback = fallback,
            onNext = onNext,
            onExit = onExit
        )
    }
}

@Composable
internal fun TutorialModeBanner() {
    val palette = LocalCalendarPalette.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.accent.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.accent.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = "Modo tutorial: los cambios son temporales",
            color = palette.accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TutorialInputBlocker(
    targetBounds: Rect?,
    fallback: TutorialFallbackFocus,
    targetEnabled: Boolean,
    onTargetClick: () -> Unit
) {
    val silentInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = silentInteraction,
                indication = null,
                onClick = {}
            )
    )

    if (targetEnabled) {
        TutorialTargetBox(
            targetBounds = targetBounds,
            fallback = fallback,
            onClick = onTargetClick
        )
    }
}

@Composable
private fun TutorialTargetBox(
    targetBounds: Rect?,
    fallback: TutorialFallbackFocus,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val modifier = if (targetBounds != null) {
        with(density) {
            Modifier
                .offset {
                    IntOffset(
                        targetBounds.left.roundToInt(),
                        targetBounds.top.roundToInt()
                    )
                }
                .size(targetBounds.width.toDp(), targetBounds.height.toDp())
        }
    } else {
        Modifier
            .fillMaxSize()
            .padding(fallback.padding)
    }

    Box(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    )
}

@Composable
private fun TutorialSpotlight(
    fallback: TutorialFallbackFocus,
    targetBounds: Rect?
) {
    val palette = LocalCalendarPalette.current
    val density = LocalDensity.current
    val paddingPx = with(density) { 8.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = 0.28f)
            if (targetBounds == null) {
                drawRect(dim)
                return@Canvas
            }

            val left = (targetBounds.left - paddingPx).coerceAtLeast(0f)
            val top = (targetBounds.top - paddingPx).coerceAtLeast(0f)
            val right = (targetBounds.right + paddingPx).coerceAtMost(size.width)
            val bottom = (targetBounds.bottom + paddingPx).coerceAtMost(size.height)

            drawRect(dim, Offset.Zero, Size(size.width, top))
            drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
            drawRect(dim, Offset(0f, top), Size(left, bottom - top))
            drawRect(dim, Offset(right, top), Size(size.width - right, bottom - top))
        }

        val focusModifier = if (targetBounds != null) {
            with(density) {
                Modifier
                    .offset {
                        IntOffset(
                            (targetBounds.left - paddingPx).roundToInt(),
                            (targetBounds.top - paddingPx).roundToInt()
                        )
                    }
                    .size(
                        (targetBounds.width + paddingPx * 2f).toDp(),
                        (targetBounds.height + paddingPx * 2f).toDp()
                    )
            }
        } else {
            Modifier
                .fillMaxSize()
                .padding(fallback.padding)
        }

        Box(modifier = focusModifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(fallback.cornerRadius))
                    .background(palette.surfaceStrong.copy(alpha = 0.10f))
                    .border(3.dp, palette.accent, RoundedCornerShape(fallback.cornerRadius))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape((fallback.cornerRadius - 6.dp).coerceAtLeast(4.dp)))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.82f),
                        RoundedCornerShape((fallback.cornerRadius - 6.dp).coerceAtLeast(4.dp))
                    )
            )
        }
    }
}

@Composable
private fun TutorialCalloutCard(
    step: TutorialStep,
    stepIndex: Int,
    totalSteps: Int,
    targetBounds: Rect?,
    fallback: TutorialFallbackFocus,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    val progress = (stepIndex + 1).toFloat() / totalSteps.toFloat()
    val lastStep = stepIndex == totalSteps - 1

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val putCardAtBottom = targetBounds?.center?.y?.let { it < screenHeightPx * 0.45f }
            ?: fallback.cardAtBottom
        val alignment = if (putCardAtBottom) Alignment.BottomCenter else Alignment.TopCenter
        val yOffset = if (putCardAtBottom) (-26).dp else 72.dp

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = palette.surface.copy(alpha = 0.98f),
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(alignment)
                .offset(y = yOffset)
                .fillMaxWidth()
                .heightIn(max = 230.dp)
                .border(1.dp, palette.softLine.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = palette.accentSoft.copy(alpha = 0.76f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.padding(7.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            color = palette.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Paso ${stepIndex + 1} de $totalSteps",
                            color = palette.textMuted.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onExit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Salir del tutorial",
                            tint = palette.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = palette.accent,
                    trackColor = palette.accentSoft.copy(alpha = 0.55f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = step.body,
                    color = palette.textMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (step.requiresTargetTap) "Toca la zona resaltada" else "Ruta guiada",
                        color = palette.accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onExit,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textMuted)
                        ) {
                            Text("Salir")
                        }
                        Button(
                            onClick = onNext,
                            enabled = !step.requiresTargetTap,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text(if (lastStep) "Terminar" else "Siguiente")
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = if (lastStep) {
                                    Icons.Rounded.Done
                                } else {
                                    Icons.AutoMirrored.Rounded.ArrowForward
                                },
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TutorialFallbackFocus(
    val padding: Dp,
    val cornerRadius: Dp,
    val cardAtBottom: Boolean = false
)

private fun TutorialFocusTarget.fallback(): TutorialFallbackFocus = when (this) {
    TutorialFocusTarget.WHOLE_APP -> TutorialFallbackFocus(
        padding = 24.dp,
        cornerRadius = 28.dp,
        cardAtBottom = false
    )

    TutorialFocusTarget.TOP_MENU,
    TutorialFocusTarget.SEARCH_BUTTON,
    TutorialFocusTarget.SEARCH_FIELD -> TutorialFallbackFocus(
        padding = 28.dp,
        cornerRadius = 26.dp,
        cardAtBottom = true
    )

    TutorialFocusTarget.ROBOFER,
    TutorialFocusTarget.AGENDA_TAB,
    TutorialFocusTarget.WEEK_TAB,
    TutorialFocusTarget.TASKS_TAB,
    TutorialFocusTarget.MONTH_TAB -> TutorialFallbackFocus(
        padding = 26.dp,
        cornerRadius = 30.dp,
        cardAtBottom = false
    )

    TutorialFocusTarget.ADD_PANEL,
    TutorialFocusTarget.CREATE_EVENT_BUTTON,
    TutorialFocusTarget.EDIT_PANEL -> TutorialFallbackFocus(
        padding = 18.dp,
        cornerRadius = 22.dp,
        cardAtBottom = false
    )

    TutorialFocusTarget.AGENDA_LIST,
    TutorialFocusTarget.WEEK_VIEW,
    TutorialFocusTarget.MONTH_VIEW -> TutorialFallbackFocus(
        padding = 22.dp,
        cornerRadius = 22.dp,
        cardAtBottom = false
    )
}

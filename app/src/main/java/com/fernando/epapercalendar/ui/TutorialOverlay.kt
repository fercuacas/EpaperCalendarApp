package com.fernando.epapercalendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fernando.epapercalendar.calendar.AppSection
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class TutorialDemoAction {
    CREATE_READY,
    EVENT_CREATED,
    EDIT_GYM_READY,
    TASK_CREATED,
    TASK_COMPLETED,
    SEARCH_READY,
    WEEK_READY,
    MONTH_READY,
    MENU_READY
}

internal enum class TutorialFocusTarget {
    WHOLE_APP,
    TOP_MENU,
    SEARCH_BUTTON,
    ROBOFER,
    DATE_STRIP,
    FILTERS,
    AGENDA_LIST,
    ADD_PANEL,
    EDITOR_BASICS,
    EDITOR_MORE_OPTIONS,
    EDITOR_ADVANCED,
    EDIT_ACTIONS,
    CREATE_EVENT_BUTTON,
    AGENDA_TAB,
    WEEK_TAB,
    TASKS_TAB,
    TASKS_VIEW,
    TASK_ROW,
    SEARCH_FIELD,
    WEEK_VIEW,
    MONTH_TAB,
    MONTH_VIEW,
    EDIT_PANEL
}

internal enum class TutorialPreview {
    MENU,
    EPAPER
}

internal data class TutorialStep(
    val title: String,
    val body: String,
    val chapter: String,
    val actionLabel: String = "Toca la zona resaltada",
    val section: AppSection = AppSection.AGENDA,
    val openCreatePanel: Boolean = false,
    val revealAdvancedOptions: Boolean = false,
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val demoAction: TutorialDemoAction? = null,
    val preview: TutorialPreview? = null,
    val focusTarget: TutorialFocusTarget = TutorialFocusTarget.WHOLE_APP,
    val requiresTargetTap: Boolean = true
)

internal val tutorialSteps = listOf(
    TutorialStep(
        title = "Bienvenido a Robofer",
        body = "En unos minutos conocerás el flujo completo. Todo lo que crees o marques aquí es temporal: tu calendario real no cambiará.",
        chapter = "Primeros pasos",
        focusTarget = TutorialFocusTarget.WHOLE_APP,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Muévete por los días",
        body = "La tira empieza en lunes. Toca cualquier fecha para consultar ese día; los puntos indican que hay eventos.",
        chapter = "Primeros pasos",
        focusTarget = TutorialFocusTarget.DATE_STRIP,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Filtra sin perderte",
        body = "En Semana y Mes puedes filtrar por Hoy, Semana o Categorías. En Agenda y Tareas siempre verás todo, sin filtros ocultos. Limpiar restaura todos los eventos.",
        chapter = "Primeros pasos",
        section = AppSection.WEEK,
        focusTarget = TutorialFocusTarget.FILTERS,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Crea con Robofer",
        body = "El botón central abre el creador desde cualquier sección y mantiene visible la navegación inferior.",
        chapter = "Crear un evento",
        focusTarget = TutorialFocusTarget.ROBOFER
    ),
    TutorialStep(
        title = "Empieza por lo esencial",
        body = "Escribe un título o usa una plantilla. También puedes escribir frases como “Gym mañana 18:30”; después ajusta fecha, hora y categoría.",
        chapter = "Crear un evento",
        openCreatePanel = true,
        demoAction = TutorialDemoAction.CREATE_READY,
        focusTarget = TutorialFocusTarget.EDITOR_BASICS,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Abre Más opciones",
        body = "Lo habitual queda arriba y los ajustes menos frecuentes se mantienen recogidos para que el formulario siga siendo rápido.",
        chapter = "Crear un evento",
        actionLabel = "Toca Más opciones",
        openCreatePanel = true,
        demoAction = TutorialDemoAction.CREATE_READY,
        focusTarget = TutorialFocusTarget.EDITOR_MORE_OPTIONS
    ),
    TutorialStep(
        title = "Planifica con detalle",
        body = "Aquí defines todo el día o varios días, duración, recordatorio, repetición, personas, ubicación o enlace y notas.",
        chapter = "Crear un evento",
        openCreatePanel = true,
        revealAdvancedOptions = true,
        demoAction = TutorialDemoAction.CREATE_READY,
        focusTarget = TutorialFocusTarget.EDITOR_ADVANCED,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Crea el evento demo",
        body = "El botón solo se activa cuando el título, las fechas y la hora son válidos. Este Gym se guardará únicamente en el tutorial.",
        chapter = "Crear un evento",
        actionLabel = "Toca Crear evento",
        openCreatePanel = true,
        revealAdvancedOptions = true,
        demoAction = TutorialDemoAction.CREATE_READY,
        focusTarget = TutorialFocusTarget.CREATE_EVENT_BUTTON
    ),
    TutorialStep(
        title = "Tu agenda se actualiza",
        body = "El evento aparece en su fecha con hora, categoría y detalles. Tócalo para abrir la edición.",
        chapter = "Organizar",
        actionLabel = "Toca el evento Gym",
        demoAction = TutorialDemoAction.EVENT_CREATED,
        focusTarget = TutorialFocusTarget.AGENDA_LIST
    ),
    TutorialStep(
        title = "Edita sin empezar de cero",
        body = "Cambia cualquier dato y guarda. Abajo puedes duplicar, mover a mañana o borrar. En eventos repetidos, la app pregunta si cambia una ocasión o toda la serie.",
        chapter = "Organizar",
        openCreatePanel = true,
        revealAdvancedOptions = true,
        demoAction = TutorialDemoAction.EDIT_GYM_READY,
        focusTarget = TutorialFocusTarget.EDIT_PANEL,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Separa las tareas",
        body = "Las tareas tienen su propia vista para que lo pendiente no se mezcle con la lectura del calendario.",
        chapter = "Organizar",
        actionLabel = "Toca Tareas",
        demoAction = TutorialDemoAction.TASK_CREATED,
        focusTarget = TutorialFocusTarget.TASKS_TAB
    ),
    TutorialStep(
        title = "Completa una tarea",
        body = "Las pendientes aparecen primero. Toca el control de la tarea demo para marcarla como completada.",
        chapter = "Organizar",
        actionLabel = "Marca la tarea",
        section = AppSection.TASKS,
        demoAction = TutorialDemoAction.TASK_CREATED,
        focusTarget = TutorialFocusTarget.TASK_ROW
    ),
    TutorialStep(
        title = "Pendiente y completado",
        body = "Las completadas quedan tachadas y pueden ocultarse o mostrarse. Toca una tarea para editar sus datos.",
        chapter = "Organizar",
        section = AppSection.TASKS,
        demoAction = TutorialDemoAction.TASK_COMPLETED,
        focusTarget = TutorialFocusTarget.TASKS_VIEW,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Encuentra cualquier evento",
        body = "En Semana y Mes, la lupa busca por título, categoría, personas, ubicación y notas.",
        chapter = "Encontrar y explorar",
        section = AppSection.WEEK,
        actionLabel = "Toca la lupa",
        demoAction = TutorialDemoAction.SEARCH_READY,
        focusTarget = TutorialFocusTarget.SEARCH_BUTTON
    ),
    TutorialStep(
        title = "Busca Gym",
        body = "Toca el campo: el tutorial escribirá “gym” para que veas el filtro aplicado al instante.",
        chapter = "Encontrar y explorar",
        section = AppSection.WEEK,
        actionLabel = "Toca el buscador",
        showSearch = true,
        demoAction = TutorialDemoAction.SEARCH_READY,
        focusTarget = TutorialFocusTarget.SEARCH_FIELD
    ),
    TutorialStep(
        title = "Resultado filtrado",
        body = "La semana muestra solo las coincidencias. Cierra la búsqueda o pulsa Limpiar para volver a ver todo.",
        chapter = "Encontrar y explorar",
        section = AppSection.WEEK,
        showSearch = true,
        searchQuery = "gym",
        demoAction = TutorialDemoAction.SEARCH_READY,
        focusTarget = TutorialFocusTarget.WEEK_VIEW,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Cambia a Semana",
        body = "La vista semanal coloca eventos por día y hora y reserva una fila para los que duran varios días.",
        chapter = "Encontrar y explorar",
        actionLabel = "Toca Semana",
        demoAction = TutorialDemoAction.WEEK_READY,
        focusTarget = TutorialFocusTarget.WEEK_TAB
    ),
    TutorialStep(
        title = "Lee la semana de un vistazo",
        body = "Los bloques usan color y abreviaturas para no cortarse. Toca un día o evento y la app abre su fecha en Agenda.",
        chapter = "Encontrar y explorar",
        section = AppSection.WEEK,
        demoAction = TutorialDemoAction.WEEK_READY,
        focusTarget = TutorialFocusTarget.WEEK_VIEW,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Cambia a Mes",
        body = "El mes sirve para detectar carga, días libres y eventos largos sin entrar en cada fecha.",
        chapter = "Encontrar y explorar",
        actionLabel = "Toca Mes",
        section = AppSection.WEEK,
        demoAction = TutorialDemoAction.MONTH_READY,
        focusTarget = TutorialFocusTarget.MONTH_TAB
    ),
    TutorialStep(
        title = "Planifica a largo plazo",
        body = "Cada día muestra sus colores y los eventos de varios días aparecen como rangos. Toca una fecha para ver su detalle debajo y un evento para editarlo.",
        chapter = "Encontrar y explorar",
        section = AppSection.CALENDAR,
        demoAction = TutorialDemoAction.MONTH_READY,
        focusTarget = TutorialFocusTarget.MONTH_VIEW,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Abre el menú",
        body = "Aquí encontrarás la ayuda, la apariencia y la preparación de datos para tu pantalla e-paper.",
        chapter = "Personalizar y sincronizar",
        actionLabel = "Toca el menú",
        demoAction = TutorialDemoAction.MENU_READY,
        focusTarget = TutorialFocusTarget.TOP_MENU
    ),
    TutorialStep(
        title = "Ayuda y apariencia",
        body = "Puedes repetir este tutorial cuando quieras y elegir un tema visual. El cambio de tema es inmediato; durante esta guía también sería temporal.",
        chapter = "Personalizar y sincronizar",
        demoAction = TutorialDemoAction.MENU_READY,
        preview = TutorialPreview.MENU,
        focusTarget = TutorialFocusTarget.WHOLE_APP,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Tu pantalla e-paper",
        body = "Generar payload prepara un resumen JSON del calendario para el dispositivo. No lo envía por sí solo: primero puedes revisarlo.",
        chapter = "Personalizar y sincronizar",
        demoAction = TutorialDemoAction.MENU_READY,
        preview = TutorialPreview.EPAPER,
        focusTarget = TutorialFocusTarget.WHOLE_APP,
        requiresTargetTap = false
    ),
    TutorialStep(
        title = "Ya conoces el flujo completo",
        body = "Fechas, filtros, creación, edición, tareas, búsqueda, semana, mes, temas y e-paper. Al terminar volverás exactamente a tu calendario real.",
        chapter = "Listo",
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
    onExit: () -> Unit,
    safeInsets: WindowInsets = WindowInsets.safeDrawing
) {
    val fallback = step.focusTarget.fallback()
    val targetBounds = targets[step.focusTarget]
    var previewBounds by remember(step.preview) { mutableStateOf<Rect?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        TutorialSpotlight(fallback = fallback, targetBounds = targetBounds)
        step.preview?.let { preview ->
            TutorialFeaturePreview(preview = preview, safeInsets = safeInsets, onBounds = { previewBounds = it })
        }
        TutorialInputBlocker(
            targetBounds = targetBounds,
            fallback = fallback,
            targetEnabled = step.requiresTargetTap,
            onTargetClick = onTargetClick
        )
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = TutorialCardState(step, stepIndex),
            transitionSpec = {
                val movingForward = targetState.stepIndex >= initialState.stepIndex
                val enterOffset: (Int) -> Int = { width -> if (movingForward) width / 8 else -width / 8 }
                val exitOffset: (Int) -> Int = { width -> if (movingForward) -width / 8 else width / 8 }
                (fadeIn(tween(220)) + slideInHorizontally(tween(320), enterOffset)) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(240), exitOffset))
            },
            label = "tutorialStep"
        ) { cardState ->
            val cardFallback = cardState.step.focusTarget.fallback().let { stepFallback ->
                if (cardState.step.preview != null) stepFallback.copy(cardAtBottom = true) else stepFallback
            }
            TutorialCalloutCard(
                step = cardState.step,
                stepIndex = cardState.stepIndex,
                totalSteps = totalSteps,
                targetBounds = if (cardState.step.preview != null) previewBounds
                    else targets[cardState.step.focusTarget].takeUnless { cardState.step.focusTarget == TutorialFocusTarget.WHOLE_APP },
                fallback = cardFallback,
                safeInsets = safeInsets,
                onPrevious = onPrevious,
                onNext = onNext,
                onExit = onExit
            )
        }
    }
}

private data class TutorialCardState(
    val step: TutorialStep,
    val stepIndex: Int
)

@Composable
private fun TutorialFeaturePreview(preview: TutorialPreview, safeInsets: WindowInsets, onBounds: (Rect) -> Unit) {
    val palette = LocalCalendarPalette.current

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(safeInsets).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.surface,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.45f)
                .testTag("tutorial_preview")
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .border(1.dp, palette.accent.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
        ) {
            when (preview) {
                TutorialPreview.MENU -> {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Menú",
                            color = palette.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TutorialPreviewRow("Ayuda", "Repite el tutorial cuando lo necesites")
                        TutorialPreviewRow("Apariencia", "Cambia el tema visual al instante")
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            listOf(palette.surfaceStrong, palette.accentSoft, palette.accent, palette.text).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(1.dp, palette.softLine, CircleShape)
                                )
                            }
                        }
                    }
                }

                TutorialPreview.EPAPER -> {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Vista previa e-paper",
                            color = palette.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.surfaceStrong.copy(alpha = 0.86f)
                        ) {
                            Text(
                                text = "{\n  \"deviceView\": \"WEEK\",\n  \"events\": [ ... ],\n  \"syncing\": false\n}",
                                color = palette.textMuted,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Generar payload prepara los datos; el envío al dispositivo es un paso separado.",
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialPreviewRow(title: String, subtitle: String) {
    val palette = LocalCalendarPalette.current
    Row(
        modifier = Modifier.padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(subtitle, color = palette.textMuted, fontSize = 13.sp)
        }
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
        modifier = modifier.testTag("tutorial_target").clickable(
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
    val targetCornerRadiusPx = when {
        targetBounds == null -> with(density) { fallback.cornerRadius.toPx() }
        fallback.shape == TutorialFocusShape.OVAL -> {
            min(
                targetBounds.width + paddingPx * 2f,
                targetBounds.height + paddingPx * 2f
            ) / 2f
        }
        else -> with(density) { fallback.cornerRadius.toPx() }
    }
    val animatedLeft by animateFloatAsState(
        targetValue = targetBounds?.left ?: 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "tutorialFocusLeft"
    )
    val animatedTop by animateFloatAsState(
        targetValue = targetBounds?.top ?: 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "tutorialFocusTop"
    )
    val animatedRight by animateFloatAsState(
        targetValue = targetBounds?.right ?: 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "tutorialFocusRight"
    )
    val animatedBottom by animateFloatAsState(
        targetValue = targetBounds?.bottom ?: 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "tutorialFocusBottom"
    )
    val animatedCornerRadius by animateFloatAsState(
        targetValue = targetCornerRadiusPx,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "tutorialFocusCorner"
    )
    val dimAlpha by animateFloatAsState(
        targetValue = if (targetBounds == null) 0.30f else 0.40f,
        animationSpec = tween(260),
        label = "tutorialDim"
    )
    val animatedBounds = if (targetBounds == null) {
        null
    } else {
        Rect(animatedLeft, animatedTop, animatedRight, animatedBottom)
    }
    val pulseTransition = rememberInfiniteTransition(label = "tutorialFocusPulse")
    val focusAlpha by pulseTransition.animateFloat(
        initialValue = 0.68f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tutorialFocusAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = dimAlpha)
            if (animatedBounds == null) {
                drawRect(dim)
                return@Canvas
            }

            val left = (animatedBounds.left - paddingPx).coerceAtLeast(0f)
            val top = (animatedBounds.top - paddingPx).coerceAtLeast(0f)
            val right = (animatedBounds.right + paddingPx).coerceAtMost(size.width)
            val bottom = (animatedBounds.bottom + paddingPx).coerceAtMost(size.height)
            val safeRadius = animatedCornerRadius
                .coerceAtLeast(0f)
                .coerceAtMost(min((right - left) / 2f, (bottom - top) / 2f))
            val screenPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            val focusPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        cornerRadius = CornerRadius(safeRadius, safeRadius)
                    )
                )
            }
            drawPath(
                path = Path.combine(PathOperation.Difference, screenPath, focusPath),
                color = dim
            )
        }

        val focusWidthPx = (animatedBounds?.width ?: 0f) + paddingPx * 2f
        val focusHeightPx = (animatedBounds?.height ?: 0f) + paddingPx * 2f
        val safeCornerRadiusPx = if (animatedBounds == null) {
            animatedCornerRadius
        } else {
            animatedCornerRadius.coerceAtMost(min(focusWidthPx, focusHeightPx) / 2f)
        }
        val focusCornerRadius = with(density) { safeCornerRadiusPx.toDp() }
        val focusModifier = if (animatedBounds != null) {
            with(density) {
                Modifier
                    .offset {
                        IntOffset(
                            (animatedBounds.left - paddingPx).roundToInt(),
                            (animatedBounds.top - paddingPx).roundToInt()
                        )
                    }
                    .size(
                        (animatedBounds.width + paddingPx * 2f).toDp(),
                        (animatedBounds.height + paddingPx * 2f).toDp()
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
                    .clip(RoundedCornerShape(focusCornerRadius))
                    .border(
                        3.dp,
                        palette.accent.copy(alpha = focusAlpha),
                        RoundedCornerShape(focusCornerRadius)
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape((focusCornerRadius - 6.dp).coerceAtLeast(4.dp)))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.82f),
                        RoundedCornerShape((focusCornerRadius - 6.dp).coerceAtLeast(4.dp))
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
    safeInsets: WindowInsets,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    val progress by animateFloatAsState(
        targetValue = (stepIndex + 1).toFloat() / totalSteps.toFloat(),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "tutorialProgress"
    )
    val lastStep = stepIndex == totalSteps - 1

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val margin = with(density) { 12.dp.toPx() }
    val horizontalMargin = with(density) { 16.dp.toPx() }
    val bodyScroll = key(stepIndex) { rememberScrollState() }
    TutorialCalloutContainer(
        safeAreaInsets = Rect(
            safeInsets.getLeft(density, direction) + horizontalMargin,
            safeInsets.getTop(density) + margin,
            safeInsets.getRight(density, direction) + horizontalMargin,
            safeInsets.getBottom(density) + margin
        ),
        target = targetBounds,
        gap = with(density) { 20.dp.toPx() },
        minimumHeight = with(density) { (220.dp * maxOf(1f, fontScale)).toPx() },
        maximumHeight = with(density) { (360.dp * maxOf(1f, fontScale)).toPx() },
        preferBottom = fallback.cardAtBottom,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = palette.surface,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tutorial_callout")
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
                            text = "${step.chapter} · Paso ${stepIndex + 1} de $totalSteps",
                            color = palette.textMuted.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
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
                Column(Modifier.weight(1f, fill = false).verticalScroll(bodyScroll).testTag("tutorial_body")) {
                    Text(
                        text = step.body,
                        color = palette.textMuted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    if (step.requiresTargetTap) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.accentSoft.copy(alpha = 0.46f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TouchApp,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = step.actionLabel,
                                    color = palette.accent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                TutorialNavigation(stepIndex > 0, !step.requiresTargetTap, lastStep, onPrevious, onNext)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorialNavigation(
    showPrevious: Boolean,
    showNext: Boolean,
    lastStep: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (stacked) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showPrevious) {
                OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f).testTag("tutorial_previous"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textMuted)) {
                    Text("Atrás", maxLines = 1)
                }
            }
            if (showNext) {
                Button(onClick = onNext, modifier = Modifier.weight(1f).testTag("tutorial_next"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)) {
                    Text(if (lastStep) "Terminar" else "Siguiente", maxLines = 1)
                }
            }
        }
    }
}

private enum class TutorialFocusShape {
    ROUNDED,
    OVAL
}

private data class TutorialFallbackFocus(
    val padding: Dp,
    val cornerRadius: Dp,
    val cardAtBottom: Boolean = false,
    val shape: TutorialFocusShape = TutorialFocusShape.ROUNDED
)

private fun TutorialFocusTarget.fallback(): TutorialFallbackFocus = when (this) {
    TutorialFocusTarget.WHOLE_APP -> TutorialFallbackFocus(
        padding = 24.dp,
        cornerRadius = 28.dp,
        cardAtBottom = false
    )

    TutorialFocusTarget.TOP_MENU,
    TutorialFocusTarget.SEARCH_BUTTON -> TutorialFallbackFocus(
        padding = 28.dp,
        cornerRadius = 32.dp,
        cardAtBottom = true,
        shape = TutorialFocusShape.OVAL
    )

    TutorialFocusTarget.SEARCH_FIELD,
    TutorialFocusTarget.FILTERS -> TutorialFallbackFocus(
        padding = 28.dp,
        cornerRadius = 26.dp,
        cardAtBottom = true
    )

    TutorialFocusTarget.ROBOFER -> TutorialFallbackFocus(
        padding = 26.dp,
        cornerRadius = 44.dp,
        cardAtBottom = false,
        shape = TutorialFocusShape.OVAL
    )

    TutorialFocusTarget.AGENDA_TAB,
    TutorialFocusTarget.WEEK_TAB,
    TutorialFocusTarget.TASKS_TAB,
    TutorialFocusTarget.MONTH_TAB -> TutorialFallbackFocus(
        padding = 26.dp,
        cornerRadius = 30.dp,
        cardAtBottom = false
    )

    TutorialFocusTarget.ADD_PANEL,
    TutorialFocusTarget.EDITOR_BASICS,
    TutorialFocusTarget.EDITOR_MORE_OPTIONS,
    TutorialFocusTarget.EDITOR_ADVANCED,
    TutorialFocusTarget.EDIT_ACTIONS,
    TutorialFocusTarget.CREATE_EVENT_BUTTON,
    TutorialFocusTarget.EDIT_PANEL -> TutorialFallbackFocus(
        padding = 18.dp,
        cornerRadius = 22.dp,
        cardAtBottom = false
    )

    TutorialFocusTarget.DATE_STRIP,
    TutorialFocusTarget.AGENDA_LIST,
    TutorialFocusTarget.TASKS_VIEW,
    TutorialFocusTarget.TASK_ROW,
    TutorialFocusTarget.WEEK_VIEW,
    TutorialFocusTarget.MONTH_VIEW -> TutorialFallbackFocus(
        padding = 22.dp,
        cornerRadius = 22.dp,
        cardAtBottom = false
    )
}

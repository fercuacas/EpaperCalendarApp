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
import androidx.compose.ui.platform.testTag
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
internal fun AgendaSection(
    state: CalendarState,
    onEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit,
    onCreate: () -> Unit,
    personalContentEnabled: Boolean = true
) {
    val allDayEvents = state.allDayEventsFor(state.selectedDate)
    val timedEvents = state.timedEventsFor(state.selectedDate)
    val dayEvents = allDayEvents + timedEvents

    SummaryPanel(taskCount = dayEvents.count { it.category == EventCategory.TASK && !it.done },
        personalContentEnabled = personalContentEnabled, selectedDate = state.selectedDate)
    Spacer(modifier = Modifier.height(14.dp))
    AgendaPanel(
        date = state.selectedDate,
        title = smartDayHeader(state.selectedDate),
        allDayEvents = allDayEvents,
        events = timedEvents,
        onEventClick = onEventClick,
        onQuickCreate = onQuickCreate,
        onCreate = onCreate
    )
}

@Composable
internal fun SummaryPanel(
    taskCount: Int,
    personalContentEnabled: Boolean = true,
    selectedDate: LocalDate = LocalDate.now()
) {
    SummaryPanelContent(taskCount, rememberPersonalDayContent(selectedDate, personalContentEnabled))
}

@Composable
internal fun SummaryPanelContent(
    taskCount: Int,
    content: PersonalDayUi
) {
    val palette = LocalCalendarPalette.current
    val shape = RoundedCornerShape(24.dp)

    Surface(
        shape = shape,
        color = palette.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agenda_summary")
            .shadow(4.dp, shape, ambientColor = palette.accent.copy(alpha = 0.10f)),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = content.phrase,
                        modifier = Modifier.testTag("personal_daily_phrase"),
                        color = palette.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp
                    )
                    Text(
                        text = if (taskCount == 1) "1 tarea pendiente" else "$taskCount tareas pendientes",
                        color = palette.textMuted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
                PersonalContentImage(content, Modifier.size(104.dp))
            }
        }
    }
}

@Composable
internal fun AgendaPanel(
    date: LocalDate,
    title: String,
    allDayEvents: List<CalendarEvent>,
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit,
    onCreate: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surface.copy(alpha = 0.86f),
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(TutorialFocusTarget.AGENDA_LIST),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                color = palette.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (allDayEvents.isNotEmpty()) {
                AllDayEventsSection(events = allDayEvents, onEventClick = onEventClick)
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (events.isEmpty() && allDayEvents.isEmpty()) {
                UsefulEmptyState(
                    icon = Icons.Rounded.Event,
                    title = "Sin eventos",
                    subtitle = "Tu día está libre. Crea un evento o usa una plantilla.",
                    actions = quickEventTemplates.take(4),
                    onQuickCreate = onQuickCreate,
                    primaryActionLabel = "Crear evento",
                    onPrimaryAction = onCreate
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            DayTimeline(date = date, events = events, onEventClick = onEventClick)
        }
    }
}

@Composable
internal fun AllDayEventsSection(
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit
) {
    val palette = LocalCalendarPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Todo el día",
            color = palette.textMuted,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
        events.forEach { event ->
            val accent = Color(event.category.accentColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .clickable { onEventClick(event) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        color = palette.text,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ImportedCalendarLabel(event)
                    Text(
                        text = if (event.spansMultipleDays) {
                            "${formatShortDate(event.date)} - ${formatShortDate(event.endDate)}"
                        } else {
                            event.category.label
                        },
                        color = palette.textMuted.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun EventCard(
    event: CalendarEvent,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    val accent = Color(event.category.accentColor)
    val soft = Color(event.category.softColor)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (compact) palette.surfaceStrong.copy(alpha = 0.68f) else soft.copy(alpha = 0.72f),
        modifier = modifier
            .height((if (compact) 84 else 96).dp + if (event.isReadOnly) 20.dp else 0.dp)
            .clickable(onClick = onClick)
            .border(1.dp, accent.copy(alpha = if (compact) 0.12f else 0.42f), RoundedCornerShape(8.dp)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.62f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.7f))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    color = palette.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 16.sp else 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (event.done) TextDecoration.LineThrough else TextDecoration.None
                )
                ImportedCalendarLabel(event)
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = eventMetaLabelRich(event),
                    color = palette.textMuted.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact && event.category == EventCategory.FOCUS) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = event.category.label,
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.42f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            CategoryIcon(category = event.category, size = if (compact) 42.dp else 54.dp)
        }
    }
}

@Composable
internal fun TasksSection(
    state: CalendarState,
    onEventClick: (CalendarEvent) -> Unit,
    onToggleDone: (String) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    val tasks = state.taskEvents()
    val pendingTasks = tasks.filterNot { it.done }
    val completedTasks = tasks.filter { it.done }
    var showCompleted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.tutorialTarget(TutorialFocusTarget.TASKS_VIEW),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = palette.surface.copy(alpha = 0.86f),
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Tareas",
                    color = palette.accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (tasks.isEmpty()) {
                    UsefulEmptyState(
                        icon = Icons.Rounded.CheckBox,
                        title = "Sin tareas",
                        subtitle = "Añade una tarea o usa una plantilla.",
                        actions = listOf(quickEventTemplates.first { it.category == EventCategory.TASK }),
                        onQuickCreate = onQuickCreate
                    )
                } else {
                    Text(
                        text = "Pendientes (${pendingTasks.size})",
                        color = palette.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (pendingTasks.isEmpty()) {
                        Text(
                            text = "No tienes tareas pendientes.",
                            color = palette.textMuted.copy(alpha = 0.84f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    pendingTasks.forEach { event ->
                        TaskRow(
                            event = event,
                            onToggleDone = { onToggleDone(event.id) },
                            onEventClick = { onEventClick(event) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (completedTasks.isNotEmpty()) {
                        TextButton(
                            onClick = { showCompleted = !showCompleted },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (showCompleted) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showCompleted) {
                                    "Ocultar completadas"
                                } else {
                                    "Mostrar completadas (${completedTasks.size})"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AnimatedVisibility(visible = showCompleted) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                completedTasks.forEach { event ->
                                    TaskRow(
                                        event = event,
                                        onToggleDone = { onToggleDone(event.id) },
                                        onEventClick = { onEventClick(event) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskRow(
    event: CalendarEvent,
    onToggleDone: () -> Unit,
    onEventClick: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(TutorialFocusTarget.TASK_ROW)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceStrong.copy(alpha = 0.62f))
            .clickable(onClick = onEventClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleDone, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = if (event.done) Icons.Rounded.CheckCircle else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (event.done) "Marcar como pendiente" else "Marcar como completada",
                tint = if (event.done) palette.success else palette.accent
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = palette.text,
                fontWeight = FontWeight.Bold,
                textDecoration = if (event.done) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(
                text = "${formatShortDate(event.date)} · ${event.startLabel}",
                color = palette.textMuted.copy(alpha = 0.82f),
                fontSize = 14.sp
            )
        }
        Icon(Icons.Rounded.Edit, contentDescription = "Editar tarea", tint = palette.textMuted.copy(alpha = 0.68f))
    }
}

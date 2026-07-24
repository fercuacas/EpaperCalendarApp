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
internal fun AgendaSection(
    state: CalendarState,
    onEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit
) {
    val allDayEvents = state.allDayEventsFor(state.selectedDate)
    val timedEvents = state.timedEventsFor(state.selectedDate)
    val dayEvents = allDayEvents + timedEvents

    SummaryPanel(
        selectedDate = state.selectedDate,
        eventCount = dayEvents.size,
        quickEvents = dayEvents.take(3)
    )
    Spacer(modifier = Modifier.height(14.dp))
    AgendaPanel(
        title = smartDayHeader(state.selectedDate),
        allDayEvents = allDayEvents,
        events = timedEvents,
        onEventClick = onEventClick,
        onQuickCreate = onQuickCreate
    )
}

@Composable
internal fun SummaryPanel(
    selectedDate: LocalDate,
    eventCount: Int,
    quickEvents: List<CalendarEvent>
) {
    val palette = LocalCalendarPalette.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surface.copy(alpha = 0.78f),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(8.dp), ambientColor = palette.accent.copy(alpha = 0.12f)),
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HomeIllustration(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp, top = 8.dp)
                    .size(width = 168.dp, height = 136.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, top = 22.dp, end = 138.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = greetingForNow(),
                        color = palette.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = palette.sun,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (eventCount == 1) {
                        "Tienes 1 evento hoy"
                    } else {
                        "Tienes $eventCount eventos hoy"
                    },
                    color = palette.textMuted.copy(alpha = 0.78f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(26.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (quickEvents.isEmpty()) {
                        QuickEventChip(
                            title = "Día libre",
                            time = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            color = Color(0xFF4BAA7D)
                        )
                    } else {
                        quickEvents.forEach { event ->
                            QuickEventChip(
                                title = event.title,
                                time = event.startLabel,
                                color = Color(event.category.accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickEventChip(
    title: String,
    time: String,
    color: Color
) {
    val palette = LocalCalendarPalette.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceStrong.copy(alpha = 0.72f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryDot(color = color, size = 30.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = palette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(112.dp)
            )
            Text(
                text = time,
                color = palette.textMuted.copy(alpha = 0.82f),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
internal fun AgendaPanel(
    title: String,
    allDayEvents: List<CalendarEvent>,
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit
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
                if (events.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            if (events.isEmpty() && allDayEvents.isEmpty()) {
                UsefulEmptyState(
                    icon = Icons.Rounded.Event,
                    title = "Sin eventos",
                    subtitle = "Empieza con una plantilla rápida.",
                    actions = quickEventTemplates.take(4),
                    onQuickCreate = onQuickCreate
                )
            } else if (events.isNotEmpty()) {
                events.forEachIndexed { index, event ->
                    TimelineEventRow(
                        event = event,
                        isCurrent = index == 0,
                        onClick = { onEventClick(event) }
                    )
                    if (index != events.lastIndex) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
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
                    Text(
                        text = if (event.spansMultipleDays) {
                            "${event.date} - ${event.endDate}"
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
internal fun TimelineEventRow(
    event: CalendarEvent,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(72.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = event.startLabel,
                color = if (isCurrent) palette.accent else palette.textMuted.copy(alpha = 0.76f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
            Text(
                text = event.endLabel,
                color = palette.textMuted.copy(alpha = 0.58f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (isCurrent) palette.accent else palette.softLine)
        )
        Spacer(modifier = Modifier.width(12.dp))
        EventCard(
            event = event,
            modifier = Modifier.weight(1f),
            onClick = onClick
        )
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
            .height(if (compact) 84.dp else 96.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    tasks.forEach { event ->
                        TaskRow(
                            event = event,
                            onToggleDone = { onToggleDone(event.id) },
                            onEventClick = { onEventClick(event) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceStrong.copy(alpha = 0.62f))
            .clickable(onClick = onEventClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleDone, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = if (event.done) Icons.Rounded.Done else Icons.Rounded.CheckBox,
                contentDescription = "Completar",
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
                text = "${event.date} · ${event.startLabel}",
                color = palette.textMuted.copy(alpha = 0.62f),
                fontSize = 13.sp
            )
        }
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = palette.textMuted.copy(alpha = 0.42f))
    }
}

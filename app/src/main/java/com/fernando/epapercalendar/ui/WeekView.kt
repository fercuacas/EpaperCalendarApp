package com.fernando.epapercalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fernando.epapercalendar.calendar.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter

@Composable
internal fun WeekSection(
    state: CalendarState,
    onImportedEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    val weekStart = state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
    val weekEnd = weekStart.plusDays(6)
    val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
    val spanEvents = state.spanOccurrencesInRange(weekStart, weekEnd)
    val eventsByDate = weekDates.associateWith { date -> state.timedEventsFor(date) }
    val openDay: (LocalDate) -> Unit = { date ->
        state.selectDate(date)
        state.selectSection(AppSection.AGENDA)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surface.copy(alpha = 0.86f),
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(TutorialFocusTarget.WEEK_VIEW),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            WeekOverviewHeader(
                weekStart = weekStart,
                weekEnd = weekEnd,
                eventCount = eventsByDate.values.sumOf { it.size },
                spanCount = spanEvents.size,
                onPrevious = { state.navigateWeek(-1) },
                onNext = { state.navigateWeek(1) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            WeekDaysHeader(weekDates, state.selectedDate, openDay)
            if (spanEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Todo el día y varios días",
                    color = palette.textMuted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                WeekSpanRows(
                    occurrences = spanEvents,
                    weekStart = weekStart,
                    onImportedEventClick = onImportedEventClick,
                    onDateSelected = openDay
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (eventsByDate.values.all { it.isEmpty() }) {
                UsefulEmptyState(
                    icon = Icons.Rounded.ViewWeek,
                    title = "Semana libre",
                    subtitle = "Planifica algo sin salir de esta vista.",
                    actions = quickEventTemplates,
                    onQuickCreate = onQuickCreate
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            WeekTimeline(
                dates = weekDates,
                eventsByDate = eventsByDate,
                onImportedEventClick = onImportedEventClick,
                onDateSelected = openDay
            )
        }
    }
}

@Composable
private fun WeekOverviewHeader(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    eventCount: Int,
    spanCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceStrong.copy(alpha = 0.5f))
            .border(1.dp, palette.softLine.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", calendarLocale)
        PeriodNavigation(
            title = "Semana",
            subtitle = "${weekStart.format(format)} – ${weekEnd.format(format)}",
            previousDescription = "Semana anterior", nextDescription = "Semana siguiente",
            onPrevious = onPrevious, onNext = onNext
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            WeekMetric(label = "Horas", value = eventCount.toString())
            Spacer(modifier = Modifier.width(8.dp))
            WeekMetric(label = "Largos", value = spanCount.toString())
        }
    }
}

@Composable
private fun WeekMetric(label: String, value: String) {
    val palette = LocalCalendarPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.accentSoft.copy(alpha = 0.42f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(label, color = palette.textMuted.copy(alpha = 0.86f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
internal fun WeekDaysHeader(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(42.dp))
        dates.forEach { date ->
            val selected = date == selectedDate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.accent else Color.Transparent)
                    .clickable { onDateSelected(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekdayShort(date.dayOfWeek).take(1),
                    color = if (selected) Color.White else palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    color = if (selected) Color.White else palette.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
internal fun WeekSpanRows(
    occurrences: List<CalendarOccurrence>,
    weekStart: LocalDate,
    onImportedEventClick: (CalendarEvent) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height((occurrences.size.coerceAtMost(4) * 30 + 4).dp)
    ) {
        val dayWidth = ((maxWidth - 42.dp).value / 7f).dp
        occurrences.take(4).forEachIndexed { rowIndex, occurrence ->
            val visibleStart = if (occurrence.startDate.isBefore(weekStart)) weekStart else occurrence.startDate
            val visibleEnd = if (occurrence.endDate.isAfter(weekStart.plusDays(6))) weekStart.plusDays(6) else occurrence.endDate
            val startIndex = (visibleStart.toEpochDay() - weekStart.toEpochDay()).toInt().coerceIn(0, 6)
            val dayCount = (visibleEnd.toEpochDay() - visibleStart.toEpochDay()).toInt().coerceAtLeast(0) + 1
            val accent = Color(occurrence.event.category.accentColor)
            Row(
                modifier = Modifier
                    .offset(x = 42.dp + dayWidth * startIndex, y = (rowIndex * 30).dp)
                    .width(dayWidth * dayCount - 4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                    .semantics {
                        contentDescription = occurrence.event.deviceSource?.let {
                            "${occurrence.event.title}. ${it.calendarName}. Consultar evento de solo lectura"
                        } ?: "${occurrence.event.title}. Abrir agenda del ${formatShortDate(visibleStart)}"
                    }
                    .clickable {
                        if (occurrence.event.isReadOnly) onImportedEventClick(occurrence.asCalendarEvent())
                        else onDateSelected(visibleStart)
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val label = (if (occurrence.event.isReadOnly) "↗ " else "") + compactEventLabel(occurrence.event.title)
                    if (maxWidth >= 22.dp && label.isNotBlank()) {
                        Text(
                            text = label,
                            color = palette.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

internal fun compactEventLabel(title: String): String {
    val words = title
        .trim()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words.first().take(2)
        else -> words.take(2).mapNotNull { it.firstOrNull() }.joinToString("")
    }.uppercase(calendarLocale)
}

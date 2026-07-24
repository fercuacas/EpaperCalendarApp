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
internal fun WeekSection(
    state: CalendarState,
    onEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    val weekStart = state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value % 7).toLong())
    val weekEnd = weekStart.plusDays(6)
    val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
    val spanEvents = state.spanOccurrencesInRange(weekStart, weekEnd)
    val eventsByDate = weekDates.associateWith { date -> state.timedEventsFor(date) }
    val activeHours = (0..23).filter { hour ->
        eventsByDate.values.any { events -> events.any { it.startMinutes / 60 == hour } }
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
                spanCount = spanEvents.size
            )
            Spacer(modifier = Modifier.height(12.dp))
            WeekDaysHeader(weekDates, state.selectedDate, state::selectDate)
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
                    onEventClick = onEventClick
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (activeHours.isEmpty()) {
                UsefulEmptyState(
                    icon = Icons.Rounded.ViewWeek,
                    title = "Semana libre",
                    subtitle = "Planifica algo sin salir de esta vista.",
                    actions = quickEventTemplates,
                    onQuickCreate = onQuickCreate
                )
            } else {
                var previousHour: Int? = null
                var previousPart: String? = null
                activeHours.forEach { hour ->
                    val part = weekPartLabel(hour)
                    if (part != previousPart) {
                        WeekPartDivider(label = part)
                        Spacer(modifier = Modifier.height(8.dp))
                        previousPart = part
                    }
                    previousHour?.let { previous ->
                        if (hour - previous > 1) {
                            WeekFreeGapRow(startHour = previous + 1, endHour = hour - 1)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    val maxEvents = weekDates.maxOf { date ->
                        eventsByDate[date].orEmpty().count { it.startMinutes / 60 == hour }
                    }
                    WeekHourRow(
                        hour = hour,
                        dates = weekDates,
                        eventsByDate = eventsByDate,
                        rowHeight = (maxEvents.coerceAtLeast(1) * 34 + 18).dp,
                        onEventClick = onEventClick
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    previousHour = hour
                }
            }
        }
    }
}

@Composable
private fun WeekOverviewHeader(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    eventCount: Int,
    spanCount: Int
) {
    val palette = LocalCalendarPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceStrong.copy(alpha = 0.5f))
            .border(1.dp, palette.softLine.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Semana",
                color = palette.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
            Text(
                text = "${weekStart.dayOfMonth} ${weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} - ${weekEnd.dayOfMonth} ${weekEnd.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                color = palette.text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        }
        WeekMetric(label = "Horas", value = eventCount.toString())
        Spacer(modifier = Modifier.width(8.dp))
        WeekMetric(label = "Largos", value = spanCount.toString())
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
        Text(label, color = palette.textMuted.copy(alpha = 0.72f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
                    fontSize = 11.sp
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
    onEventClick: (CalendarEvent) -> Unit
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
                    .clickable { onEventClick(occurrence.event) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = occurrence.event.title,
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun WeekPartDivider(label: String) {
    val palette = LocalCalendarPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(42.dp))
        Text(
            text = label,
            color = palette.textMuted.copy(alpha = 0.72f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = palette.softLine.copy(alpha = 0.62f)
        )
    }
}

@Composable
internal fun WeekFreeGapRow(startHour: Int, endHour: Int) {
    val palette = LocalCalendarPalette.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(42.dp))
        Text(
            text = if (startHour == endHour) {
                "Libre hasta las %02d:00".format(endHour + 1)
            } else {
                "Libre %02d:00-%02d:00".format(startHour, endHour + 1)
            },
            color = palette.textMuted.copy(alpha = 0.58f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceStrong.copy(alpha = 0.34f))
                .border(1.dp, palette.softLine.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
internal fun WeekHourRow(
    hour: Int,
    dates: List<LocalDate>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    rowHeight: Dp,
    onEventClick: (CalendarEvent) -> Unit
) {
    val palette = LocalCalendarPalette.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "%02d:00".format(hour),
            color = palette.textMuted.copy(alpha = 0.72f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.width(42.dp)
        )
        dates.forEach { date ->
            val events = eventsByDate[date].orEmpty().filter { it.startMinutes / 60 == hour }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceStrong.copy(alpha = 0.4f))
                    .border(1.dp, palette.softLine.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                events.forEach { event ->
                    val accent = Color(event.category.accentColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .clickable { onEventClick(event) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = event.title,
                            color = palette.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = event.startLabel,
                            color = palette.textMuted.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

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
internal fun MonthSection(
    state: CalendarState,
    onEventClick: (CalendarEvent) -> Unit,
    onQuickCreate: (QuickEventTemplate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    val monthEvents = state.monthEvents()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surface.copy(alpha = 0.86f),
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(TutorialFocusTarget.MONTH_VIEW),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            MonthOverviewHeader(
                month = state.visibleMonth,
                selectedDate = state.selectedDate,
                eventCount = monthEvents.size,
                longEventCount = monthEvents.count { it.allDayLike },
                onPrevious = { state.navigateMonth(-1) },
                onNext = { state.navigateMonth(1) }
            )
            Spacer(modifier = Modifier.height(14.dp))
            MonthGrid(
                month = state.visibleMonth,
                selectedDate = state.selectedDate,
                eventCountFor = state::eventCountFor,
                spanCountFor = state::multiDayCountFor,
                onDateSelected = state::selectDate
            )
            val selectedSpanEvents = state.spanOccurrencesInRange(state.selectedDate, state.selectedDate)
            if (selectedSpanEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                MonthRangeSummary(
                    occurrences = selectedSpanEvents,
                    selectedDate = state.selectedDate,
                    onEventClick = onEventClick
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = smartDayHeader(state.selectedDate),
                color = palette.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            val events = state.eventsFor(state.selectedDate)
            if (events.isEmpty()) {
                UsefulEmptyState(
                    icon = Icons.Rounded.Today,
                    title = "Sin eventos",
                    subtitle = "Crea algo para este día o planifica un rango.",
                    actions = listOf(
                        quickEventTemplates.first { it.category == EventCategory.WORK },
                        quickEventTemplates.first { it.category == EventCategory.FITNESS },
                        quickEventTemplates.first { it.category == EventCategory.PERSONAL }
                    ),
                    onQuickCreate = onQuickCreate
                )
            } else {
                events.forEach { event ->
                    EventCard(
                        event = event,
                        compact = true,
                        onClick = { onEventClick(event) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthOverviewHeader(
    month: YearMonth,
    selectedDate: LocalDate,
    eventCount: Int,
    longEventCount: Int,
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
        PeriodNavigation(
            title = month.month.getDisplayName(TextStyle.FULL, calendarLocale)
                .replaceFirstChar { it.titlecase(calendarLocale) } + " ${month.year}",
            subtitle = "Seleccionado: ${selectedDate.dayOfMonth}/${selectedDate.monthValue}",
            previousDescription = "Mes anterior", nextDescription = "Mes siguiente",
            onPrevious = onPrevious, onNext = onNext
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            MonthMetric(label = "Eventos", value = eventCount.toString())
            Spacer(modifier = Modifier.width(8.dp))
            MonthMetric(label = "Varios días", value = longEventCount.toString())
        }
    }
}

@Composable
private fun MonthMetric(label: String, value: String) {
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
private fun MonthRangeSummary(
    occurrences: List<CalendarOccurrence>,
    selectedDate: LocalDate,
    onEventClick: (CalendarEvent) -> Unit
) {
    val palette = LocalCalendarPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        occurrences.take(3).forEach { occurrence ->
            val event = occurrence.event
            val accent = Color(event.category.accentColor)
            val totalDays = (occurrence.endDate.toEpochDay() - occurrence.startDate.toEpochDay()).coerceAtLeast(0) + 1
            val selectedOffset = (selectedDate.toEpochDay() - occurrence.startDate.toEpochDay()).coerceIn(0, totalDays - 1)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                    .clickable { onEventClick(occurrence.asCalendarEvent()) },
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.title,
                            color = palette.text,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$totalDays días",
                            color = palette.textMuted.copy(alpha = 0.74f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    ImportedCalendarLabel(event)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.46f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(((selectedOffset + 1).toFloat() / totalDays.toFloat()).coerceIn(0.08f, 1f))
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.82f))
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${occurrence.startDate} - ${occurrence.endDate}",
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
internal fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    eventCountFor: (LocalDate) -> Int,
    spanCountFor: (LocalDate) -> Int,
    onDateSelected: (LocalDate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    val firstDay = month.atDay(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val totalCells = 42
    val cells = (0 until totalCells).map { cellIndex ->
        val dayNumber = cellIndex - leadingEmptyDays + 1
        if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { label ->
                Text(
                    text = label,
                    color = palette.textMuted.copy(alpha = 0.78f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        repeat(totalCells / 7) { week ->
            val weekDates = cells.drop(week * 7).take(7)
            MonthWeekRow(
                weekDates = weekDates,
                selectedDate = selectedDate,
                eventCountFor = eventCountFor,
                spanCountFor = spanCountFor,
                onDateSelected = onDateSelected
            )
        }
    }
}

@Composable
internal fun MonthWeekRow(
    weekDates: List<LocalDate?>,
    selectedDate: LocalDate,
    eventCountFor: (LocalDate) -> Int,
    spanCountFor: (LocalDate) -> Int,
    onDateSelected: (LocalDate) -> Unit
) {
    val palette = LocalCalendarPalette.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weekDates.forEach { date ->
                if (date == null) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    val selected = date == selectedDate
                    val isToday = date == LocalDate.now()
                    val count = eventCountFor(date)
                    val spanCount = spanCountFor(date)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    selected -> palette.accent
                                    isToday -> Color(0xFFE84D88).copy(alpha = 0.15f)
                                    else -> palette.surfaceStrong.copy(alpha = 0.55f)
                                }
                            )
                            .border(
                                width = if (isToday && !selected) 1.dp else 0.dp,
                                color = if (isToday && !selected) Color(0xFFE84D88).copy(alpha = 0.58f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onDateSelected(date) }
                            .padding(top = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = if (selected) Color.White else palette.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (spanCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color.White else palette.accent.copy(alpha = 0.82f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(if (count > 0) 5.dp else 3.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            count == 0 -> Color.Transparent
                                            selected -> Color.White
                                            else -> palette.accent
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

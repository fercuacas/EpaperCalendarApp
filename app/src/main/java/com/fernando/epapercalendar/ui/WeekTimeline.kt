package com.fernando.epapercalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.layoutWeekTimeline
import java.time.LocalDate

private val weekHourHeight = 64.dp
private val weekTimeGutter = 42.dp
private val weekTopInset = 12.dp

@Composable
internal fun WeekTimeline(
    dates: List<LocalDate>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onImportedEventClick: (CalendarEvent) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    val density = LocalDensity.current
    val initialScroll = with(density) { (weekHourHeight * 8).roundToPx() }
    val placementsByDate = remember(dates, eventsByDate) {
        dates.associateWith { layoutWeekTimeline(eventsByDate[it].orEmpty()) }
    }
    // A bounded scroll keeps the day headings visible and works inside CalendarHome's scroll.
    key(dates.firstOrNull()) {
        val scroll = rememberScrollState(initial = initialScroll)
        Box(
            modifier.fillMaxWidth().height(440.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceStrong.copy(alpha = 0.35f))
                .testTag("week_timeline")
                .verticalScroll(scroll)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(weekHourHeight * 24 + weekTopInset * 2)) {
                val dayWidth = ((maxWidth - weekTimeGutter) / 7).coerceAtLeast(1.dp)
                Canvas(Modifier.fillMaxSize()) {
                    val gutter = weekTimeGutter.toPx()
                    val step = weekHourHeight.toPx()
                    for (halfHour in 0..48) {
                        val y = weekTopInset.toPx() + halfHour * step / 2
                        drawLine(
                            color = palette.softLine.copy(alpha = if (halfHour % 2 == 0) 0.85f else 0.25f),
                            start = Offset(gutter, y), end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    for (day in 0..7) {
                        val x = gutter + dayWidth.toPx() * day
                        drawLine(palette.softLine.copy(alpha = 0.5f),
                            Offset(x, weekTopInset.toPx()), Offset(x, size.height - weekTopInset.toPx()),
                            strokeWidth = 1.dp.toPx())
                    }
                }
                for (hour in 0..24) {
                    Text(
                        text = "%02d:00".format(hour), color = palette.textMuted,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.offset(y = weekHourHeight * hour + 3.dp)
                            .width(weekTimeGutter).testTag("week_hour_$hour")
                    )
                }
                dates.forEachIndexed { dayIndex, date ->
                    Box(Modifier.offset(x = weekTimeGutter + dayWidth * dayIndex, y = weekTopInset)
                        .width(dayWidth).height(weekHourHeight * 24)
                        .semantics { contentDescription = "Abrir agenda del ${formatShortDate(date)}" }
                        .clickable { onDateSelected(date) })
                    placementsByDate[date].orEmpty().forEach { placement ->
                        val laneWidth = (dayWidth - 2.dp) / placement.columnCount
                        val blockWidth = (laneWidth * placement.columnSpan - 1.dp).coerceAtLeast(1.dp)
                        val blockHeight = weekHourHeight * ((placement.endMinute - placement.startMinute) / 60f)
                        val events = placement.events
                        val event = events.first()
                        val group = events.size > 1
                        val description = (if (group) "${events.size} eventos con el mismo inicio. " else "") +
                            events.joinToString("; ") { item ->
                                "${item.title}, ${item.startLabel}–${item.endLabel}" +
                                    (item.deviceSource?.let { ", ${it.calendarName}" } ?: "")
                            }
                        Box(
                            Modifier.offset(
                                x = weekTimeGutter + dayWidth * dayIndex + 1.dp + laneWidth * placement.column,
                                y = weekTopInset + weekHourHeight * (placement.startMinute / 60f)
                            ).width(blockWidth).height(blockHeight)
                                .semantics(mergeDescendants = true) { contentDescription = description }
                                .testTag("week_event_${date}_${placement.startMinute}")
                                .clickable {
                                    if (!group && event.isReadOnly) onImportedEventClick(event)
                                    else onDateSelected(date)
                                }
                        ) {
                            // Separate strips retain each finish time, even for equal starts.
                            Canvas(Modifier.fillMaxSize()) {
                                val stripWidth = size.width / events.size
                                events.forEachIndexed { index, item ->
                                    val duration = (item.endMinutes.coerceIn(placement.startMinute + 1, 1440) -
                                        placement.startMinute) / 60f
                                    val height = weekHourHeight.toPx() * duration
                                    val accent = Color(item.category.accentColor)
                                    val origin = Offset(stripWidth * index, 0f)
                                    val stripSize = Size(stripWidth.coerceAtLeast(0.1f), height)
                                    drawRoundRect(accent.copy(alpha = 0.24f), origin, stripSize,
                                        CornerRadius(2.dp.toPx()))
                                    drawRoundRect(accent.copy(alpha = 0.8f), origin, stripSize,
                                        CornerRadius(2.dp.toPx()), style = Stroke(0.7.dp.toPx()))
                                }
                            }
                            val abbreviation = compactEventLabel(event.title)
                            val label = if (group) events.size.toString() else
                                (if (event.isReadOnly && blockWidth >= 30.dp) "↗" else "") + abbreviation
                            Text(
                                text = label, color = palette.text,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (blockWidth >= 18.dp) 10.sp else 8.sp,
                                lineHeight = 12.sp, maxLines = 1, softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.align(Alignment.TopCenter)
                                    .padding(top = 2.dp)
                                    .testTag("week_start_${date}_${placement.startMinute}")
                            )
                        }
                    }
                }
            }
        }
    }
}

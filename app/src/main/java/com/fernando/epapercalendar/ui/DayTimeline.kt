package com.fernando.epapercalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.layoutDayTimeline
import java.time.LocalDate

private val hourHeight = 72.dp
private val timeGutter = 48.dp
private val topInset = 12.dp

@Composable
internal fun DayTimeline(
    date: LocalDate,
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    val density = LocalDensity.current
    val initialScroll = with(density) { (hourHeight * 8).roundToPx() }
    val placements = remember(events) { layoutDayTimeline(events) }
    // Reopen each selected day at 08:00, without jumping when its events refresh.
    key(date) {
        val scroll = rememberScrollState(initial = initialScroll)
        Box(
            modifier = modifier.fillMaxWidth().height(440.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceStrong.copy(alpha = 0.45f))
                .testTag("day_timeline")
                .verticalScroll(scroll)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(hourHeight * 24 + topInset * 2)) {
                val eventWidth = (maxWidth - timeGutter - 4.dp).coerceAtLeast(1.dp)
                Canvas(Modifier.fillMaxSize()) {
                    val gutter = timeGutter.toPx()
                    val step = hourHeight.toPx()
                    for (halfHour in 0..48) {
                        val y = topInset.toPx() + halfHour * step / 2
                        drawLine(
                            color = palette.softLine.copy(alpha = if (halfHour % 2 == 0) 0.9f else 0.35f),
                            start = Offset(gutter, y), end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
                for (hour in 0..24) {
                    Text(
                        text = "%02d:00".format(hour), color = palette.textMuted,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.offset(y = hourHeight * hour + 3.dp).width(timeGutter)
                    )
                }
                placements.forEach { placement ->
                    val columnWidth = eventWidth / placement.columnCount
                    val blockHeight = hourHeight * ((placement.endMinute - placement.startMinute) / 60f)
                    val event = placement.event
                    val accent = Color(event.category.accentColor)
                    val description = "${event.title}, ${event.startLabel}–${event.endLabel}" +
                        (event.deviceSource?.let { ", ${it.calendarName}" } ?: "")
                    Column(
                        modifier = Modifier
                            .offset(x = timeGutter + columnWidth * placement.column,
                                y = topInset + hourHeight * (placement.startMinute / 60f))
                            .width((columnWidth * placement.columnSpan - 3.dp).coerceAtLeast(1.dp))
                            .height((blockHeight - 2.dp).coerceAtLeast(1.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(event.category.softColor))
                            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .clickable { onEventClick(event) }
                            .semantics(mergeDescendants = true) { contentDescription = description }
                            .testTag("day_event_${event.id}")
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(event.title, color = palette.text, fontSize = 13.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = if (blockHeight >= 72.dp) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (event.done) TextDecoration.LineThrough else TextDecoration.None)
                        if (blockHeight >= 40.dp) {
                            Text("${event.startLabel}–${event.endLabel}", color = palette.textMuted,
                                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (blockHeight >= 70.dp) ImportedCalendarLabel(event)
                        if (blockHeight >= 95.dp && event.location.isNotBlank()) {
                            Text(event.location, color = palette.textMuted, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

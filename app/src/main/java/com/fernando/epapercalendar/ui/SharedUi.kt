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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

internal val calendarLocale: Locale = Locale.forLanguageTag("es-ES")
private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", calendarLocale)

internal fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

@Composable
internal fun WeekStrip(
    selectedDate: LocalDate,
    eventCountFor: (LocalDate) -> Int,
    onDateSelected: (LocalDate) -> Unit
) {
    val palette = LocalCalendarPalette.current
    val firstDate = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val dates = (0..6).map { firstDate.plusDays(it.toLong()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(TutorialFocusTarget.DATE_STRIP)
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dates.forEach { date ->
            val selected = date == selectedDate
            val events = eventCountFor(date)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) {
                            Brush.verticalGradient(listOf(palette.accentSecondary, palette.accent))
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable { onDateSelected(date) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = weekdayShort(date.dayOfWeek),
                        color = if (selected) Color.White else palette.textMuted.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (selected) Color.White else palette.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 27.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(if (events > 0) 7.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    selected && events > 0 -> Color.White
                                    events > 0 -> palette.accent
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current

    Surface(
        shape = CircleShape,
        color = palette.surface.copy(alpha = 0.82f),
        modifier = modifier.size(52.dp),
        shadowElevation = 1.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = palette.textMuted,
                modifier = Modifier.size(27.dp)
            )
        }
    }
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val palette = LocalCalendarPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accent.copy(alpha = 0.74f),
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = title, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(text = subtitle, color = palette.textMuted.copy(alpha = 0.62f), fontSize = 14.sp)
    }
}

@Composable
internal fun UsefulEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actions: List<QuickEventTemplate>,
    onQuickCreate: (QuickEventTemplate) -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    val palette = LocalCalendarPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accent.copy(alpha = 0.76f),
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = title, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(
            text = subtitle,
            color = palette.textMuted.copy(alpha = 0.82f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onPrimaryAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(primaryActionLabel, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = { onQuickCreate(template) },
                    label = { Text(template.label) },
                    leadingIcon = { CategoryDot(Color(template.category.accentColor), size = 16.dp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

@Composable
internal fun CategoryDot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.42f)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
internal fun CategoryIcon(category: EventCategory, size: Dp) {
    val accent = Color(category.accentColor)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.44f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            when (category) {
                EventCategory.COFFEE -> drawCoffee(accent)
                EventCategory.FITNESS -> drawDumbbell(accent)
                EventCategory.PERSONAL -> drawPlant(accent)
                EventCategory.TASK -> drawCheck(accent)
                EventCategory.FOCUS -> drawSpark(accent)
                EventCategory.WORK -> drawBriefcase(accent)
            }
        }
    }
}

internal fun DrawScope.drawCoffee(color: Color) {
    val cup = Size(size.width * 0.56f, size.height * 0.42f)
    drawRoundRect(
        color = color.copy(alpha = 0.85f),
        topLeft = Offset(size.width * 0.17f, size.height * 0.38f),
        size = cup,
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawCircle(
        color = color.copy(alpha = 0.22f),
        radius = size.width * 0.16f,
        center = Offset(size.width * 0.75f, size.height * 0.47f)
    )
    drawLine(
        color = color.copy(alpha = 0.52f),
        start = Offset(size.width * 0.35f, size.height * 0.18f),
        end = Offset(size.width * 0.35f, size.height * 0.30f),
        strokeWidth = 4f
    )
    drawLine(
        color = color.copy(alpha = 0.52f),
        start = Offset(size.width * 0.52f, size.height * 0.14f),
        end = Offset(size.width * 0.52f, size.height * 0.29f),
        strokeWidth = 4f
    )
}

internal fun DrawScope.drawDumbbell(color: Color) {
    drawLine(
        color = color,
        start = Offset(size.width * 0.2f, size.height * 0.52f),
        end = Offset(size.width * 0.8f, size.height * 0.42f),
        strokeWidth = 7f
    )
    drawCircle(color = color.copy(alpha = 0.8f), radius = size.width * 0.13f, center = Offset(size.width * 0.2f, size.height * 0.52f))
    drawCircle(color = color.copy(alpha = 0.8f), radius = size.width * 0.13f, center = Offset(size.width * 0.8f, size.height * 0.42f))
}

internal fun DrawScope.drawPlant(color: Color) {
    drawRoundRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(size.width * 0.25f, size.height * 0.65f),
        size = Size(size.width * 0.5f, size.height * 0.18f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawLine(color = color, start = Offset(size.width * 0.5f, size.height * 0.66f), end = Offset(size.width * 0.5f, size.height * 0.25f), strokeWidth = 5f)
    drawOval(
        color = color.copy(alpha = 0.78f),
        topLeft = Offset(size.width * 0.28f, size.height * 0.28f),
        size = Size(size.width * 0.27f, size.height * 0.22f)
    )
    drawOval(
        color = color.copy(alpha = 0.78f),
        topLeft = Offset(size.width * 0.52f, size.height * 0.18f),
        size = Size(size.width * 0.25f, size.height * 0.25f)
    )
}

internal fun DrawScope.drawCheck(color: Color) {
    drawCircle(color = color.copy(alpha = 0.16f), radius = size.minDimension * 0.42f, center = center)
    drawLine(color = color, start = Offset(size.width * 0.28f, size.height * 0.52f), end = Offset(size.width * 0.44f, size.height * 0.68f), strokeWidth = 7f)
    drawLine(color = color, start = Offset(size.width * 0.44f, size.height * 0.68f), end = Offset(size.width * 0.76f, size.height * 0.32f), strokeWidth = 7f)
}

internal fun DrawScope.drawSpark(color: Color) {
    drawCircle(color = color.copy(alpha = 0.18f), radius = size.minDimension * 0.43f, center = center)
    drawCircle(color = color, radius = size.minDimension * 0.12f, center = center)
    repeat(8) { index ->
        val angle = Math.toRadians((index * 45).toDouble())
        val start = Offset(
            x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.22f,
            y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.22f
        )
        val end = Offset(
            x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.38f,
            y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.38f
        )
        drawLine(color = color, start = start, end = end, strokeWidth = 4f)
    }
}

internal fun DrawScope.drawBriefcase(color: Color) {
    drawRoundRect(
        color = color.copy(alpha = 0.82f),
        topLeft = Offset(size.width * 0.2f, size.height * 0.35f),
        size = Size(size.width * 0.6f, size.height * 0.42f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = color.copy(alpha = 0.42f),
        topLeft = Offset(size.width * 0.36f, size.height * 0.23f),
        size = Size(size.width * 0.28f, size.height * 0.18f),
        cornerRadius = CornerRadius(6f, 6f)
    )
}

@Composable
internal fun HomeIllustration(modifier: Modifier = Modifier) {
    val palette = LocalCalendarPalette.current

    Canvas(modifier = modifier) {
        drawCircle(
            color = palette.sun.copy(alpha = 0.85f),
            radius = size.minDimension * 0.13f,
            center = Offset(size.width * 0.82f, size.height * 0.22f)
        )
        drawOval(
            color = palette.accentSoft.copy(alpha = 0.65f),
            topLeft = Offset(size.width * 0.36f, size.height * 0.36f),
            size = Size(size.width * 0.54f, size.height * 0.36f)
        )
        drawRoundRect(
            color = palette.accentSecondary.copy(alpha = 0.55f),
            topLeft = Offset(size.width * 0.08f, size.height * 0.66f),
            size = Size(size.width * 0.86f, size.height * 0.24f),
            cornerRadius = CornerRadius(30f, 30f)
        )
        val roof = Path().apply {
            moveTo(size.width * 0.46f, size.height * 0.45f)
            lineTo(size.width * 0.6f, size.height * 0.26f)
            lineTo(size.width * 0.76f, size.height * 0.45f)
            close()
        }
        drawPath(roof, color = palette.accent.copy(alpha = 0.86f))
        drawRoundRect(
            color = palette.surfaceStrong,
            topLeft = Offset(size.width * 0.49f, size.height * 0.44f),
            size = Size(size.width * 0.24f, size.height * 0.28f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = palette.accent.copy(alpha = 0.76f),
            topLeft = Offset(size.width * 0.58f, size.height * 0.57f),
            size = Size(size.width * 0.06f, size.height * 0.15f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawTree(Offset(size.width * 0.24f, size.height * 0.58f), palette.accentSecondary)
        drawTree(Offset(size.width * 0.9f, size.height * 0.62f), Color(0xFF98B94E))
    }
}

internal fun DrawScope.drawTree(root: Offset, color: Color) {
    drawRoundRect(
        color = Color(0xFF9B5837).copy(alpha = 0.65f),
        topLeft = Offset(root.x - 4f, root.y),
        size = Size(8f, size.height * 0.22f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawCircle(
        color = color.copy(alpha = 0.78f),
        radius = size.minDimension * 0.08f,
        center = Offset(root.x, root.y - size.height * 0.03f)
    )
}

internal fun weekdayShort(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "LUN"
    DayOfWeek.TUESDAY -> "MAR"
    DayOfWeek.WEDNESDAY -> "MIÉ"
    DayOfWeek.THURSDAY -> "JUE"
    DayOfWeek.FRIDAY -> "VIE"
    DayOfWeek.SATURDAY -> "SÁB"
    DayOfWeek.SUNDAY -> "DOM"
}

internal fun greetingForNow(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

internal fun eventMetaLabel(event: CalendarEvent): String {
    val primary = event.location.ifBlank { event.category.label }
    return if (event.people.isBlank()) {
        primary
    } else {
        "$primary · ${event.people}"
    }
}

internal fun eventMetaLabelRich(event: CalendarEvent): String {
    val parts = mutableListOf<String>()
    if (event.allDayLike) {
        parts += if (event.spansMultipleDays) "${event.date} - ${event.endDate}" else "Todo el día"
    }
    parts += event.location.ifBlank { event.category.label }
    if (event.people.isNotBlank()) parts += event.people
    if (event.recurrenceRule != RecurrenceRule.NONE) parts += event.recurrenceRule.label
    if (event.reminder != ReminderOption.NONE) parts += event.reminder.label
    return parts.joinToString(" · ")
}

internal fun smartDayHeader(date: LocalDate): String {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, calendarLocale)
        .uppercase(calendarLocale)
    val month = date.month.getDisplayName(TextStyle.FULL, calendarLocale)
        .uppercase(calendarLocale)
    val dateLabel = "$dayName, ${date.dayOfMonth} $month"
    val today = LocalDate.now()
    return when (date) {
        today -> "HOY · $dateLabel"
        today.plusDays(1) -> "MAÑANA · $dateLabel"
        today.plusDays(2) -> "PASADO MAÑANA · $dateLabel"
        else -> dateLabel
    }
}

internal fun EventDraft.hasAdvancedFields(): Boolean {
    return allDay ||
        endDate != date ||
        people.isNotBlank() ||
        location.isNotBlank() ||
        notes.isNotBlank() ||
        recurrenceRule != RecurrenceRule.NONE ||
        reminder != ReminderOption.NONE ||
        customRepeatDays != 7 || customWeekDays.isNotEmpty()
}

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
import java.text.Normalizer
import java.time.*
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private enum class DateFieldTarget {
    START,
    END
}

private data class NaturalEventSuggestion(
    val title: String,
    val date: LocalDate?,
    val startLabel: String?,
    val category: EventCategory?,
    val durationMinutes: Int?
)

@Composable
internal fun AddEventPanel(
    visible: Boolean,
    draft: EventDraft?,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Boolean,
    onSaveRecurring: (EventDraft, RecurrenceEditMode) -> Boolean,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(selectedDate.toString()) }
    var endDateText by remember { mutableStateOf(selectedDate.toString()) }
    var timeText by remember { mutableStateOf("09:00") }
    var durationText by remember { mutableStateOf("30") }
    var category by remember { mutableStateOf(EventCategory.WORK) }
    var people by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var recurrenceRule by remember { mutableStateOf(RecurrenceRule.NONE) }
    var customRepeatDaysText by remember { mutableStateOf("7") }
    var reminder by remember { mutableStateOf(ReminderOption.NONE) }
    var advancedVisible by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<DateFieldTarget?>(null) }
    var timePickerVisible by remember { mutableStateOf(false) }
    var pendingRecurringDraft by remember { mutableStateOf<EventDraft?>(null) }
    val parsedStartDate = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: selectedDate
    val isEditing = draft?.id != null
    val naturalSuggestion = remember(title, selectedDate, draft?.id) {
        if (draft == null) parseNaturalEvent(title, selectedDate) else null
    }

    LaunchedEffect(visible, draft?.id, selectedDate) {
        if (visible) {
            val source = draft
            title = source?.title.orEmpty()
            dateText = (source?.date ?: selectedDate).toString()
            endDateText = (source?.endDate ?: source?.date ?: selectedDate).toString()
            timeText = source?.startLabel ?: "09:00"
            val start = source?.startLabel?.let(CalendarEvent::labelToMinutes) ?: 9 * 60
            val end = source?.endLabel?.let(CalendarEvent::labelToMinutes) ?: 9 * 60 + 30
            durationText = (end - start).coerceAtLeast(30).toString()
            category = source?.category ?: EventCategory.WORK
            people = source?.people.orEmpty()
            location = source?.location.orEmpty()
            notes = source?.notes.orEmpty()
            allDay = source?.allDay ?: false
            recurrenceRule = source?.recurrenceRule ?: RecurrenceRule.NONE
            customRepeatDaysText = (source?.customRepeatDays ?: 7).toString()
            reminder = source?.reminder ?: ReminderOption.NONE
            advancedVisible = source?.hasAdvancedFields() == true
            hasError = false
        }
    }

    fun applyTemplate(template: QuickEventTemplate) {
        title = template.title
        category = template.category
        durationText = template.durationMinutes.toString()
        allDay = template.allDay
        if (template.allDay) {
            val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: selectedDate
            endDateText = date.toString()
        }
        hasError = false
    }

    fun buildDraft(idOverride: String?, dayOffset: Long = 0): EventDraft? {
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
        val endDate = runCatching { LocalDate.parse(endDateText.trim()) }.getOrNull()
        val startMinutes = if (allDay) 0 else CalendarEvent.labelToMinutes(timeText.trim()) ?: -1
        val durationMinutes = if (allDay) 24 * 60 - 1 else durationText.toIntOrNull() ?: -1
        val endMinutes = if (allDay) {
            23 * 60 + 59
        } else {
            (startMinutes + durationMinutes).coerceAtMost(23 * 60 + 59)
        }
        if (date == null || endDate == null || endDate.isBefore(date) || title.isBlank() || startMinutes < 0 || durationMinutes <= 0) {
            hasError = true
            return null
        }
        return EventDraft(
            id = idOverride,
            title = title,
            location = location,
            date = date.plusDays(dayOffset),
            endDate = endDate.plusDays(dayOffset),
            startLabel = if (allDay) "00:00" else timeText.trim(),
            endLabel = CalendarEvent.minutesToLabel(endMinutes),
            category = category,
            people = people,
            notes = notes,
            allDay = allDay,
            recurrenceRule = recurrenceRule,
            customRepeatDays = customRepeatDaysText.toIntOrNull()?.coerceAtLeast(1) ?: 7,
            recurrenceEndDate = draft?.recurrenceEndDate,
            excludedDates = draft?.excludedDates.orEmpty(),
            reminder = reminder,
            done = draft?.done ?: false
        )
    }

    fun saveCurrent(idOverride: String? = draft?.id, dayOffset: Long = 0) {
        val nextDraft = buildDraft(idOverride = idOverride, dayOffset = dayOffset) ?: return
        if (
            draft?.id != null &&
            draft.recurrenceRule != RecurrenceRule.NONE &&
            idOverride == draft.id &&
            dayOffset == 0L
        ) {
            pendingRecurringDraft = nextDraft
        } else {
            hasError = !onSave(nextDraft)
        }
    }

    fun applyNaturalSuggestion(suggestion: NaturalEventSuggestion) {
        title = suggestion.title
        suggestion.date?.let { date ->
            val previousDate = dateText
            dateText = date.toString()
            if (endDateText == previousDate) {
                endDateText = date.toString()
            }
        }
        suggestion.startLabel?.let { timeText = it }
        suggestion.category?.let { category = it }
        suggestion.durationMinutes?.let { durationText = it.toString() }
        hasError = false
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
            animationSpec = tween(360),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(
            animationSpec = tween(240),
            targetOffsetY = { it / 3 }
        )
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = palette.surface.copy(alpha = 0.96f),
            modifier = Modifier
                .fillMaxWidth()
                .tutorialTarget(TutorialFocusTarget.ADD_PANEL)
                .shadow(16.dp, RoundedCornerShape(8.dp), ambientColor = palette.accent.copy(alpha = 0.2f)),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 78.dp, height = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.softLine)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Editar evento" else "Crear evento",
                        color = palette.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.surfaceStrong.copy(alpha = 0.54f))
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = palette.textMuted.copy(alpha = 0.72f))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                SheetTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        hasError = false
                    },
                    label = "Título del evento",
                    placeholder = "p. ej. Reunión de equipo",
                    icon = Icons.Rounded.Event,
                    keyboardType = KeyboardType.Text,
                )
                naturalSuggestion?.let { suggestion ->
                    NaturalSuggestionChip(
                        suggestion = suggestion,
                        onApply = { applyNaturalSuggestion(suggestion) }
                    )
                }
                if (!isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickEventTemplates.forEach { template ->
                            FilterChip(
                                selected = title == template.title && category == template.category,
                                onClick = { applyTemplate(template) },
                                label = { Text(template.label) },
                                leadingIcon = { CategoryDot(Color(template.category.accentColor), size = 16.dp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DatePickerField(
                        label = "Fecha",
                        date = parsedStartDate,
                        onClick = { datePickerTarget = DateFieldTarget.START },
                        modifier = Modifier.weight(1f)
                    )
                    if (!allDay) {
                        TimePickerField(
                            label = "Hora",
                            timeLabel = timeText,
                            onClick = { timePickerVisible = true },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                CategoryPickerField(
                    selectedCategory = category,
                    onCategorySelected = { category = it }
                )
                TextButton(
                    onClick = { advancedVisible = !advancedVisible },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (advancedVisible) "Menos opciones" else "Más opciones",
                        color = palette.accent,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                AnimatedVisibility(visible = advancedVisible) {
                    Column {
                        FilterChip(
                            selected = allDay,
                            onClick = {
                                allDay = !allDay
                                if (allDay) {
                                    endDateText = dateText
                                }
                                hasError = false
                            },
                            label = { Text("Todo el día / varios días") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Today, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DatePickerField(
                                label = "Fecha fin",
                                date = runCatching { LocalDate.parse(endDateText.trim()) }.getOrNull() ?: parsedStartDate,
                                onClick = { datePickerTarget = DateFieldTarget.END },
                                modifier = Modifier.weight(1f)
                            )
                            if (!allDay) {
                                DurationPickerField(
                                    minutes = durationText.toIntOrNull() ?: 30,
                                    onSelected = {
                                        durationText = it.toString()
                                        hasError = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SheetPickerField(
                                label = "Recordatorio",
                                selected = reminder,
                                options = ReminderOption.entries,
                                labelFor = { it.label },
                                icon = Icons.Rounded.Notifications,
                                onSelected = { reminder = it },
                                modifier = Modifier.weight(1f)
                            )
                            SheetPickerField(
                                label = "Repetición",
                                selected = recurrenceRule,
                                options = RecurrenceRule.entries,
                                labelFor = { it.label },
                                icon = Icons.Rounded.Repeat,
                                onSelected = { recurrenceRule = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (recurrenceRule == RecurrenceRule.CUSTOM) {
                            DurationPickerField(
                                label = "Cada X días",
                                minutes = customRepeatDaysText.toIntOrNull() ?: 7,
                                options = listOf(1, 2, 3, 4, 7, 14, 21, 30),
                                suffix = "días",
                                icon = Icons.Rounded.Repeat,
                                onSelected = {
                                    customRepeatDaysText = it.toString()
                                    hasError = false
                                }
                            )
                        }
                        SheetTextField(
                            value = people,
                            onValueChange = { people = it },
                            label = "Personas",
                            placeholder = "Opcional",
                            icon = Icons.Rounded.CheckBox,
                            keyboardType = KeyboardType.Text
                        )
                        SheetTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = "Ubicación o enlace",
                            placeholder = "Opcional",
                            icon = Icons.Rounded.Search,
                            keyboardType = KeyboardType.Text
                        )
                        SheetTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Notas",
                            placeholder = "Opcional",
                            icon = Icons.Rounded.Edit,
                            keyboardType = KeyboardType.Text
                        )
                    }
                }
                datePickerTarget?.let { target ->
                    DatePickerDialogField(
                        initialDate = if (target == DateFieldTarget.START) {
                            parsedStartDate
                        } else {
                            runCatching { LocalDate.parse(endDateText.trim()) }.getOrNull() ?: parsedStartDate
                        },
                        onDismiss = { datePickerTarget = null },
                        onDateSelected = { selected ->
                            if (target == DateFieldTarget.START) {
                                val previousDate = dateText
                                dateText = selected.toString()
                                if (endDateText == previousDate || runCatching { LocalDate.parse(endDateText.trim()) }.getOrNull()?.isBefore(selected) == true) {
                                    endDateText = selected.toString()
                                }
                            } else {
                                endDateText = selected.toString()
                            }
                            hasError = false
                            datePickerTarget = null
                        }
                    )
                }
                if (timePickerVisible) {
                    TimePickerDialogField(
                        initialTime = timeText,
                        onDismiss = { timePickerVisible = false },
                        onTimeSelected = {
                            timeText = it
                            hasError = false
                            timePickerVisible = false
                        }
                    )
                }
                if (hasError) {
                    Text(
                        text = "Revisa título, fechas YYYY-MM-DD, hora HH:mm y duración.",
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                draft?.id?.let { id ->
                    EventEditActions(
                        onDuplicate = { saveCurrent(idOverride = null) },
                        onMoveTomorrow = { saveCurrent(idOverride = id, dayOffset = 1) },
                        onDelete = { onDelete(id) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { saveCurrent() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tutorialTarget(TutorialFocusTarget.CREATE_EVENT_BUTTON)
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text(if (isEditing) "Guardar cambios" else "Crear evento", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    pendingRecurringDraft?.let { pendingDraft ->
        RecurrenceEditDialog(
            onSelected = { mode ->
                hasError = !onSaveRecurring(pendingDraft, mode)
                if (!hasError) {
                    pendingRecurringDraft = null
                }
            },
            onDismiss = { pendingRecurringDraft = null }
        )
    }
}

@Composable
private fun EventEditActions(
    onDuplicate: () -> Unit,
    onMoveTomorrow: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = "Acciones del evento",
            color = palette.textMuted.copy(alpha = 0.72f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditActionCard(
                icon = Icons.Rounded.Add,
                title = "Duplicar",
                subtitle = "Copia este evento",
                onClick = onDuplicate,
                modifier = Modifier.weight(1f)
            )
            EditActionCard(
                icon = Icons.Rounded.Today,
                title = "Mañana",
                subtitle = "Mover un día",
                onClick = onMoveTomorrow,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        EditActionCard(
            icon = Icons.Rounded.Delete,
            title = "Borrar",
            subtitle = "Eliminar este evento",
            onClick = onDelete,
            destructive = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EditActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    val palette = LocalCalendarPalette.current
    val accent = if (destructive) Color(0xFFD84A4A) else palette.accent

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (destructive) Color(0xFFFFE7E2).copy(alpha = 0.68f) else palette.surfaceStrong.copy(alpha = 0.66f),
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text(subtitle, color = palette.textMuted.copy(alpha = 0.66f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NaturalSuggestionChip(
    suggestion: NaturalEventSuggestion,
    onApply: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    val details = buildList {
        suggestion.date?.let { add(relativeDateLabel(it)) }
        suggestion.startLabel?.let { add(it) }
        suggestion.category?.let { add(it.label) }
    }.joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.accentSoft.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, palette.accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .clickable(onClick = onApply),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    color = palette.text,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        color = palette.textMuted.copy(alpha = 0.76f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text("Aplicar", color = palette.accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PickerSurfaceField(
        label = label,
        value = date.toString(),
        icon = Icons.Rounded.CalendarMonth,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun TimePickerField(
    label: String,
    timeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PickerSurfaceField(
        label = label,
        value = timeLabel,
        icon = Icons.Rounded.Schedule,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun DurationPickerField(
    minutes: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Duración",
    options: List<Int> = listOf(15, 30, 45, 60, 90, 120, 180, 240),
    suffix: String = "min",
    icon: ImageVector = Icons.Rounded.Today
) {
    SheetPickerField(
        label = label,
        selected = minutes,
        options = options,
        labelFor = { "$it $suffix" },
        icon = icon,
        onSelected = onSelected,
        modifier = modifier
    )
}

@Composable
private fun PickerSurfaceField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceStrong.copy(alpha = 0.62f))
            .border(1.dp, palette.softLine.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = palette.textMuted.copy(alpha = 0.68f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = palette.text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogField(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.toEpochMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                        ?.let(onDateSelected)
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogField(
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val minutes = CalendarEvent.labelToMinutes(initialTime) ?: 9 * 60
    val pickerState = rememberTimePickerState(
        initialHour = minutes / 60,
        initialMinute = minutes % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = LocalCalendarPalette.current.surface,
        title = { Text("Escoger hora", fontWeight = FontWeight.ExtraBold) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onTimeSelected(CalendarEvent.minutesToLabel(pickerState.hour * 60 + pickerState.minute)) }) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RecurrenceEditDialog(
    onSelected: (RecurrenceEditMode) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = palette.surface,
        title = {
            Text("Editar repetición", color = palette.text, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceModeRow("Solo este evento", "Crea una excepción para este día", RecurrenceEditMode.SINGLE_OCCURRENCE, onSelected)
                RecurrenceModeRow("Este y futuros", "Parte la serie desde esta fecha", RecurrenceEditMode.THIS_AND_FUTURE, onSelected)
                RecurrenceModeRow("Toda la serie", "Actualiza todos los eventos repetidos", RecurrenceEditMode.ENTIRE_SERIES, onSelected)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = palette.textMuted, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun RecurrenceModeRow(
    title: String,
    subtitle: String,
    mode: RecurrenceEditMode,
    onSelected: (RecurrenceEditMode) -> Unit
) {
    val palette = LocalCalendarPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surfaceStrong.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(mode) }
            .border(1.dp, palette.softLine.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = palette.text, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = palette.textMuted.copy(alpha = 0.72f), fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = palette.accent)
        },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = keyboardType
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.softLine.copy(alpha = 0.72f),
            focusedContainerColor = palette.surfaceStrong.copy(alpha = 0.72f),
            unfocusedContainerColor = palette.surfaceStrong.copy(alpha = 0.62f)
        )
    )
}

@Composable
internal fun <T> SheetPickerField(
    label: String,
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    icon: ImageVector,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceStrong.copy(alpha = 0.62f))
                .border(1.dp, palette.softLine.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = palette.textMuted.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = labelFor(selected),
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = palette.textMuted.copy(alpha = 0.58f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelFor(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun CategoryPickerField(
    selectedCategory: EventCategory,
    onCategorySelected: (EventCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalendarPalette.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceStrong.copy(alpha = 0.62f))
                .border(1.dp, palette.softLine.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDot(Color(selectedCategory.accentColor), size = 28.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Categoría",
                    color = palette.textMuted.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedCategory.label,
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = palette.textMuted.copy(alpha = 0.58f))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            EventCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    leadingIcon = { CategoryDot(Color(category.accentColor), size = 22.dp) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun parseNaturalEvent(input: String, baseDate: LocalDate): NaturalEventSuggestion? {
    if (input.length < 4) return null
    val plain = input.plainText()
    val timeRegex = Regex("""\b([01]?\d|2[0-3])(?::|h|\.)([0-5]\d)\b""")
    val timeMatch = timeRegex.find(plain)
    val parsedTime = timeMatch?.let {
        "%02d:%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    val parsedDate = when {
        "pasado mañana" in plain -> baseDate.plusDays(2)
        "mañana" in plain -> baseDate.plusDays(1)
        "hoy" in plain -> LocalDate.now()
        else -> parseWeekday(plain, baseDate)
    }
    val parsedCategory = when {
        listOf("gym", "gimnasio", "entreno", "entrenamiento", "workout").any { it in plain } -> EventCategory.FITNESS
        listOf("cafe", "coffee").any { it in plain } -> EventCategory.COFFEE
        listOf("tarea", "task", "pendiente").any { it in plain } -> EventCategory.TASK
        listOf("reunion", "meeting", "sync", "trabajo").any { it in plain } -> EventCategory.WORK
        listOf("foco", "focus", "deep work").any { it in plain } -> EventCategory.FOCUS
        listOf("viaje", "vacaciones", "personal").any { it in plain } -> EventCategory.PERSONAL
        else -> null
    }
    if (parsedDate == null && parsedTime == null && parsedCategory == null) return null

    var cleanTitle = input
    timeMatch?.value?.let { cleanTitle = cleanTitle.replace(Regex(Regex.escape(it), RegexOption.IGNORE_CASE), " ") }
    listOf("pasado mañana", "pasado mañana", "mañana", "mañana", "hoy").forEach { token ->
        cleanTitle = cleanTitle.replace(Regex("""\b${Regex.escape(token)}\b""", RegexOption.IGNORE_CASE), " ")
    }
    weekdayNames().forEach { token ->
        cleanTitle = cleanTitle.replace(Regex("""\b${Regex.escape(token)}\b""", RegexOption.IGNORE_CASE), " ")
    }
    cleanTitle = cleanTitle.replace(Regex("\\s+"), " ").trim()
    if (cleanTitle.isBlank()) {
        cleanTitle = parsedCategory?.label ?: "Nuevo evento"
    }

    return NaturalEventSuggestion(
        title = cleanTitle,
        date = parsedDate,
        startLabel = parsedTime,
        category = parsedCategory,
        durationMinutes = when (parsedCategory) {
            EventCategory.FITNESS -> 60
            EventCategory.COFFEE -> 45
            EventCategory.WORK -> 45
            EventCategory.FOCUS -> 90
            EventCategory.TASK -> 30
            EventCategory.PERSONAL -> 60
            null -> null
        }
    )
}

private fun parseWeekday(text: String, baseDate: LocalDate): LocalDate? {
    val targets = mapOf(
        "lunes" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "sábado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )
    val day = targets.entries.firstOrNull { it.key.plainText() in text }?.value ?: return null
    val delta = ((day.value - baseDate.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
    return baseDate.plusDays(delta.toLong())
}

private fun weekdayNames(): List<String> = listOf(
    "lunes",
    "martes",
    "miércoles",
    "miercoles",
    "jueves",
    "viernes",
    "sábado",
    "sabado",
    "domingo"
)

private fun relativeDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        today.plusDays(2) -> "Pasado mañana"
        else -> date.toString()
    }
}

private fun LocalDate.toEpochMillis(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun String.plainText(): String {
    return Normalizer.normalize(lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
}

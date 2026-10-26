package com.fernando.epapercalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fernando.epapercalendar.calendar.RecurrenceDeleteMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekdaySelector(selected: Set<DayOfWeek>, onSelected: (Set<DayOfWeek>) -> Unit) {
    val palette = LocalCalendarPalette.current
    Column {
        Text("Repetir cada semana los días:", color = palette.text, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.values().forEach { day ->
                val fullName = when (day) {
                    DayOfWeek.SATURDAY -> "sábados"
                    DayOfWeek.SUNDAY -> "domingos"
                    else -> day.getDisplayName(TextStyle.FULL, calendarLocale)
                }
                FilterChip(
                    selected = day in selected,
                    onClick = { onSelected(if (day in selected) selected - day else selected + day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, calendarLocale)) },
                    modifier = Modifier.semantics { contentDescription = "Repetir los $fullName" }
                )
            }
        }
        if (selected.isEmpty()) Text("Selecciona al menos un día de la semana.", color = palette.accent)
    }
}

@Composable
internal fun RecurrenceDeleteDialog(
    date: LocalDate,
    endDate: LocalDate,
    onSelected: (RecurrenceDeleteMode) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Borrar evento repetido", color = palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("¿Qué quieres borrar?", color = palette.text)
                OutlinedButton(onClick = { onSelected(RecurrenceDeleteMode.SINGLE_OCCURRENCE) }, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Solo este día")
                        Text(if (endDate == date) "Solo la aparición del ${formatShortDate(date)}; el resto continúa."
                            else "Solo la aparición del ${formatShortDate(date)} al ${formatShortDate(endDate)}; el resto continúa.")
                    }
                }
                OutlinedButton(onClick = { onSelected(RecurrenceDeleteMode.ENTIRE_SERIES) }, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Toda la serie")
                        Text("Elimina todas las apariciones de este evento, pasadas y futuras.")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

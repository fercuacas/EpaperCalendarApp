package com.fernando.epapercalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppMenuDialog(
    selectedTheme: CalendarThemeOption,
    syncStatus: String,
    onStartTutorial: () -> Unit,
    onThemeSelected: (CalendarThemeOption) -> Unit,
    onGeneratePayload: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = palette.surface,
        title = {
            Text(
                text = "Menú",
                color = palette.text,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MenuSectionTitle(
                    icon = Icons.AutoMirrored.Rounded.Help,
                    title = "Ayuda",
                    subtitle = "Aprende el flujo sin tocar tus datos"
                )
                AppMenuActionCard(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Tutorial guiado",
                    subtitle = "Recorre la app con datos temporales",
                    accent = true,
                    onClick = onStartTutorial
                )

                MenuSectionTitle(
                    icon = Icons.Rounded.Sync,
                    title = "Dispositivo e-paper",
                    subtitle = "Prepara la información para la pantalla"
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.surfaceStrong.copy(alpha = 0.72f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.softLine.copy(alpha = 0.64f), RoundedCornerShape(8.dp)),
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sincronización",
                                    color = palette.text,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = syncStatus,
                                    color = palette.textMuted.copy(alpha = 0.72f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGeneratePayload,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generar payload")
                        }
                    }
                }

                MenuSectionTitle(
                    icon = Icons.Rounded.Palette,
                    title = "Apariencia",
                    subtitle = "Elige el tono visual de la app"
                )
                CalendarThemeOption.entries.forEach { option ->
                    ThemeOptionRow(
                        option = option,
                        selected = option == selectedTheme,
                        onClick = { onThemeSelected(option) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun MenuSectionTitle(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val palette = LocalCalendarPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, color = palette.accent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(subtitle, color = palette.textMuted.copy(alpha = 0.66f), fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AppMenuActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    val borderColor = if (accent) palette.accent.copy(alpha = 0.34f) else palette.softLine.copy(alpha = 0.64f)
    val background = if (accent) palette.accentSoft.copy(alpha = 0.38f) else palette.surfaceStrong.copy(alpha = 0.72f)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(subtitle, color = palette.textMuted.copy(alpha = 0.72f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    option: CalendarThemeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val optionPalette = option.palette

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            optionPalette.accentSoft.copy(alpha = 0.46f)
        } else {
            optionPalette.surfaceStrong.copy(alpha = 0.84f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) optionPalette.accent else optionPalette.softLine,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    optionPalette.surfaceStrong,
                    optionPalette.accentSoft,
                    optionPalette.accent,
                    optionPalette.text
                ).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(1.dp, optionPalette.softLine.copy(alpha = 0.8f), CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(optionPalette.name, color = optionPalette.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    optionPalette.description,
                    color = optionPalette.textMuted.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = "Tema seleccionado",
                    tint = optionPalette.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
internal fun NotificationPermissionDialog(
    onActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = palette.surface,
        title = {
            Text(
                text = "Activa notificaciones",
                color = palette.text,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Text(
                text = "Robofer puede avisarte antes de reuniones, tareas y eventos importantes.",
                color = palette.textMuted,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Button(
                onClick = onActivate,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Activar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ahora no", color = palette.textMuted, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun PayloadPreviewDialog(
    payload: String,
    onDismiss: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = palette.surface,
        title = {
            Text("Payload e-paper", color = palette.text, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Text(
                text = payload,
                color = palette.text,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceStrong.copy(alpha = 0.7f))
                    .padding(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Cerrar")
            }
        }
    )
}

package com.fernando.epapercalendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CoralDark,
    onPrimary = Night,
    secondary = CocoaDark,
    tertiary = Sun,
    background = Night,
    surface = Color(0xFF30211A),
    onBackground = Color(0xFFFFF7EC),
    onSurface = Color(0xFFFFF7EC)
)

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    secondary = Cocoa,
    tertiary = Sun,
    background = Cream,
    surface = Cream,
    onPrimary = Color.White,
    onSecondary = Cream,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink,
    outline = SoftLine
)

@Composable
fun EpaperCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

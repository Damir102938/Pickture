package com.moviepicker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Фирменный индиго (фон иконки). */
val Indigo = Color(0xFF5865F2)

/** Светлый индиго для кнопок и акцентов на тёмном фоне. */
val Accent = Color(0xFF8C96FF)
val Gold = Color(0xFFFFC857)
val MatchHigh = Color(0xFF4CD08A)
val MatchMid = Color(0xFFFFB547)
val MatchLow = Color(0xFF9A9CB8)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF10154A),
    primaryContainer = Color(0xFF2E3590),
    onPrimaryContainer = Color(0xFFDDE0FF),
    secondary = Color(0xFFB9BEFF),
    onSecondary = Color(0xFF1C2160),
    secondaryContainer = Color(0xFF2E3590),
    onSecondaryContainer = Color(0xFFDDE0FF),
    tertiary = Gold,
    onTertiary = Color(0xFF2B2000),
    background = Color(0xFF0F1020),
    onBackground = Color(0xFFE6E7F5),
    surface = Color(0xFF0F1020),
    onSurface = Color(0xFFE6E7F5),
    surfaceVariant = Color(0xFF23253A),
    onSurfaceVariant = Color(0xFFB4B7CF),
    surfaceContainerLowest = Color(0xFF0B0C18),
    surfaceContainerLow = Color(0xFF14152A),
    surfaceContainer = Color(0xFF181A2C),
    surfaceContainerHigh = Color(0xFF23253A),
    surfaceContainerHighest = Color(0xFF2D3048),
    outline = Color(0xFF5A5D78),
    outlineVariant = Color(0xFF34374D),
)

@Composable
fun MoviePickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

package com.moviepicker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF6B4A)
val Gold = Color(0xFFFFC857)
val MatchHigh = Color(0xFF4CD08A)
val MatchMid = Color(0xFFFFB547)
val MatchLow = Color(0xFF9A93A6)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2219),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Gold,
    onSecondary = Color(0xFF2B2000),
    secondaryContainer = Color(0xFF3A3146),
    onSecondaryContainer = Color(0xFFECE0F5),
    background = Color(0xFF121016),
    onBackground = Color(0xFFEDE7F2),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFEDE7F2),
    surfaceVariant = Color(0xFF26222E),
    onSurfaceVariant = Color(0xFFB9B0C4),
    surfaceContainer = Color(0xFF1C1922),
    surfaceContainerHigh = Color(0xFF26222E),
    surfaceContainerHighest = Color(0xFF302B39),
    outline = Color(0xFF5C5566),
    outlineVariant = Color(0xFF3A3442),
)

@Composable
fun MoviePickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

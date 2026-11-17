package com.moviepicker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.picktureTypography

/** Фирменный индиго (фон иконки). */
val Indigo = Brand.Indigo

/** Светлый индиго для кнопок и акцентов на тёмном фоне. */
val Accent = Brand.Accent
val Gold = Brand.Gold
val MatchHigh = Brand.MatchHigh
val MatchMid = Brand.MatchMid
val MatchLow = Brand.MatchLow

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
    onTertiary = Brand.OnGold,
    background = Brand.Background,
    onBackground = Brand.Text,
    surface = Brand.Background,
    onSurface = Brand.Text,
    surfaceVariant = Brand.TicketHigh,
    onSurfaceVariant = Color(0xFFB4B7CF),
    surfaceContainerLowest = Color(0xFF0B0C18),
    surfaceContainerLow = Color(0xFF14152A),
    surfaceContainer = Brand.Ticket,
    surfaceContainerHigh = Brand.TicketHigh,
    surfaceContainerHighest = Color(0xFF2D3048),
    outline = Color(0xFF5A5D78),
    outlineVariant = Brand.Perforation,
)

/** Острые, «билетные» скругления вместо мягких материаловских. */
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun MoviePickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = picktureTypography(), shapes = shapes, content = content)
}

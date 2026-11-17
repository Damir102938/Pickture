package com.moviepicker.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.moviepicker.domain.Images
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.UserMark
import retrofit2.HttpException
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Тема (та же палитра индиго, что на Android) ----------

val Accent = Color(0xFF8C96FF)
val Gold = Color(0xFFFFC857)
val MatchHigh = Color(0xFF4CD08A)
val MatchMid = Color(0xFFFFB547)
val MatchLow = Color(0xFF9A9CB8)

val PicktureColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF10154A),
    primaryContainer = Color(0xFF2E3590),
    onPrimaryContainer = Color(0xFFDDE0FF),
    secondary = Color(0xFFB9BEFF),
    onSecondary = Color(0xFF1C2160),
    secondaryContainer = Color(0xFF2E3590),
    onSecondaryContainer = Color(0xFFDDE0FF),
    background = Color(0xFF0F1020),
    onBackground = Color(0xFFE6E7F5),
    surface = Color(0xFF0F1020),
    onSurface = Color(0xFFE6E7F5),
    surfaceVariant = Color(0xFF23253A),
    onSurfaceVariant = Color(0xFFB4B7CF),
    surfaceContainerLow = Color(0xFF14152A),
    surfaceContainer = Color(0xFF181A2C),
    surfaceContainerHigh = Color(0xFF23253A),
    surfaceContainerHighest = Color(0xFF2D3048),
    outline = Color(0xFF5A5D78),
    outlineVariant = Color(0xFF34374D),
)

// ---------- Утилиты ----------

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
}

fun Throwable.userMessage(): String = when (this) {
    is HttpException -> when (code()) {
        401 -> "TMDB отклонил ключ (401). Проверьте ключ в настройках."
        429 -> "Слишком много запросов к TMDB, попробуйте через минуту."
        else -> "Ошибка сервера TMDB (${code()})."
    }
    is IOException -> "Нет связи с TMDB. Проверьте интернет; в некоторых регионах TMDB открывается только через VPN."
    else -> localizedMessage ?: "Что-то пошло не так"
}

fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

private val ruLocale = Locale.forLanguageTag("ru")
private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy", ruLocale)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", ruLocale)

fun LocalDate.formatFull(): String = format(fullDate)

fun Title.subtitle(): String {
    val today = LocalDate.now()
    val d = date
    val datePart = when {
        d == null -> "Дата неизвестна"
        d.isAfter(today) -> if (d.year == today.year) d.format(shortDate) else d.format(fullDate)
        else -> d.year.toString()
    }
    return if (type == MediaType.TV) "$datePart · сериал" else datePart
}

fun matchColor(match: Int): Color = when {
    match >= 75 -> MatchHigh
    match >= 55 -> MatchMid
    else -> MatchLow
}

// ---------- Компоненты ----------

@Composable
fun Poster(path: String?, modifier: Modifier = Modifier, size: String = "w342") {
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        AsyncImage(
            model = Images.poster(path, size),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MarkIcon(mark: UserMark?, modifier: Modifier = Modifier) {
    val (icon, tint) = when {
        mark == null -> return
        mark.reaction == Reaction.LIKE -> Icons.Filled.Favorite to MaterialTheme.colorScheme.primary
        mark.reaction == Reaction.DISLIKE -> Icons.Filled.ThumbDown to MatchLow
        mark.inWatchlist -> Icons.Filled.Bookmark to Gold
        else -> return
    }
    Box(
        modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp)) }
}

@Composable
fun TitleCard(title: Title, match: Int?, mark: UserMark?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box {
            Poster(title.posterPath, Modifier.fillMaxWidth())
            if (title.voteCount >= 5 && title.rating > 0) {
                Badge("%.1f".format(title.rating), Gold, Modifier.align(Alignment.TopStart).padding(8.dp), Icons.Filled.Star)
            }
            MarkIcon(mark, Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (match != null) {
                Badge("$match%", matchColor(match), Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(title.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ряд карточек без горизонтальной прокрутки: показывается столько, сколько помещается по ширине. */
@Composable
fun FittingRow(
    titles: List<Title>,
    cardWidth: Dp = 168.dp,
    content: @Composable (Title, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 16.dp
        val count = ((maxWidth + gap) / (cardWidth + gap)).toInt().coerceAtLeast(1)
        val width = (maxWidth - gap * (count - 1)) / count
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            titles.take(count).forEach { content(it, Modifier.width(width)) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, height: Dp? = null) {
    Box(
        if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
fun MessageBox(
    title: String,
    text: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageBox("Не удалось загрузить", message, modifier, "Повторить", onRetry)

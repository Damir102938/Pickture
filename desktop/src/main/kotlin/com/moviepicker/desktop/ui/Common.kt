package com.moviepicker.desktop.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import com.moviepicker.desktop.ui.design.Brand
import com.moviepicker.desktop.ui.design.MarqueeButton
import com.moviepicker.desktop.ui.design.MarqueeLoader
import com.moviepicker.desktop.ui.design.Perforation
import com.moviepicker.desktop.ui.design.PkType
import com.moviepicker.desktop.ui.design.TicketShape
import com.moviepicker.desktop.ui.design.pressClickable
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

// ---------- Тема (та же палитра «Афиши», что на Android) ----------

val Accent = Brand.Accent
val Gold = Brand.Gold
val MatchHigh = Brand.MatchHigh
val MatchMid = Brand.MatchMid
val MatchLow = Brand.MatchLow

val PicktureColors = darkColorScheme(
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
    surfaceContainerLow = Color(0xFF14152A),
    surfaceContainer = Brand.Ticket,
    surfaceContainerHigh = Brand.TicketHigh,
    surfaceContainerHighest = Color(0xFF2D3048),
    outline = Color(0xFF5A5D78),
    outlineVariant = Brand.Perforation,
)

/** Острые, «билетные» скругления вместо мягких материаловских. */
val PicktureShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
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

fun matchColor(match: Int): Color = Brand.match(match)

// ---------- Компоненты ----------

/** Постер. [ratio] = null — без фиксированных пропорций (размер задаёт [modifier]). */
@Composable
fun Poster(
    path: String?,
    modifier: Modifier = Modifier,
    size: String = "w342",
    shape: Shape = RoundedCornerShape(6.dp),
    ratio: Float? = 2f / 3f,
) {
    Box(
        modifier.then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier).clip(shape)
            .background(Brand.TicketHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Movie, null, tint = Brand.Perforation, modifier = Modifier.size(40.dp))
        AsyncImage(
            model = Images.poster(path, size),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Отметка пользователя на постере — маленький штамп в углу. */
@Composable
fun MarkIcon(mark: UserMark?, modifier: Modifier = Modifier) {
    val (icon, tint) = when {
        mark == null -> return
        mark.reaction == Reaction.LIKE -> Icons.Filled.Favorite to Brand.Accent
        mark.reaction == Reaction.DISLIKE -> Icons.Filled.ThumbDown to Brand.MatchLow
        mark.inWatchlist -> Icons.Filled.Bookmark to Brand.Gold
        else -> return
    }
    Box(
        modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp)) }
}

private val StubHeight = 28.dp

/** Карточка-билет: постер, линия отрыва и корешок с процентом совпадения и рейтингом TMDB. */
@Composable
fun TitleCard(title: Title, match: Int?, mark: UserMark?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.pressClickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp, notchOffset = StubHeight, fromEnd = true))
                .background(Brand.Ticket),
        ) {
            Box {
                Poster(title.posterPath, Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp))
                MarkIcon(mark, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            Perforation(inset = 7.dp)
            Row(
                Modifier.fillMaxWidth().height(StubHeight).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (match != null) Text("$match%", style = PkType.number, color = matchColor(match))
                else Text(if (title.type == MediaType.TV) "СЕРИАЛ" else "ФИЛЬМ", style = PkType.tiny, color = Brand.TextMuted)
                Spacer(Modifier.weight(1f))
                if (title.voteCount >= 5 && title.rating > 0) {
                    Text("★ %.1f".format(title.rating), style = PkType.number, color = Brand.Gold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Место под две строки названия и дату — карточки в ряду одной высоты, дата сразу под названием
        val nameStyle = MaterialTheme.typography.bodyLarge
        val dateStyle = MaterialTheme.typography.bodySmall
        val captionHeight = with(LocalDensity.current) { nameStyle.lineHeight.toDp() * 2 + dateStyle.lineHeight.toDp() }
        Column(Modifier.heightIn(min = captionHeight)) {
            Text(title.name, style = nameStyle, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(title.subtitle(), style = dateStyle, color = Brand.TextMuted, maxLines = 1)
        }
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
    ) { MarqueeLoader() }
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
        Text(title.uppercase(), style = PkType.section, color = Brand.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Perforation(Modifier.width(72.dp), color = Brand.Gold)
        if (text != null) {
            Spacer(Modifier.height(10.dp))
            Text(text, textAlign = TextAlign.Center, color = Brand.TextMuted)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            MarqueeButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageBox("Не удалось загрузить", message, modifier, "Повторить", onRetry)

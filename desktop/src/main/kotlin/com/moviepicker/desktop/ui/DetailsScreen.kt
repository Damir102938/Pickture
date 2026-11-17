package com.moviepicker.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moviepicker.desktop.DesktopContainer
import com.moviepicker.domain.Images
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.TitleDetails
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun DetailsScreen(c: DesktopContainer, nav: Navigator, key: TitleKey) {
    val scope = rememberCoroutineScope()
    var state by remember(key) { mutableStateOf<LoadState<TitleDetails>>(LoadState.Loading) }
    val mark by remember(key) { c.repository.observeMark(key) }.collectAsState(UserMark())
    val profile by c.tasteProfile.collectAsState()
    val marks by c.marks.collectAsState()

    fun load() {
        state = LoadState.Loading
        scope.launch {
            state = runCatching { c.repository.details(key) }.fold(
                onSuccess = { LoadState.Success(it) },
                onFailure = { LoadState.Error(it.userMessage()) },
            )
        }
    }
    LaunchedEffect(key) { load() }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Error -> ErrorBox(s.message, onRetry = ::load, modifier = Modifier.align(Alignment.Center))
            is LoadState.Success -> {
                val d = s.data
                val t = d.title
                val scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Box(Modifier.fillMaxWidth().height(380.dp)) {
                        AsyncImage(
                            model = Images.backdrop(t.backdropPath, "w1280") ?: Images.poster(t.posterPath, "w780"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.3f),
                                    0.55f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background,
                                )
                            )
                        )
                    }
                    Row(Modifier.padding(horizontal = 40.dp).offset(y = (-140).dp)) {
                        Poster(t.posterPath, Modifier.width(240.dp), size = "w500")
                        Spacer(Modifier.width(32.dp))
                        Column(Modifier.weight(1f).padding(top = 120.dp).widthIn(max = 900.dp)) {
                            Text(t.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            t.originalName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(8.dp))
                            Text(metaLine(d), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (t.voteCount > 0) {
                                    Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(20.dp))
                                    Text(" %.1f".format(t.rating), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("  ${t.voteCount} оценок TMDB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(20.dp))
                                }
                                profile.matchPercent(t)?.let {
                                    Text("Совпадение $it%", color = matchColor(it), fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ToggleButton(
                                    if (mark.reaction == Reaction.LIKE) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    "Нравится", mark.reaction == Reaction.LIKE,
                                ) { react(c, scope, d, mark, Reaction.LIKE) }
                                ToggleButton(
                                    if (mark.reaction == Reaction.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                    "Не моё", mark.reaction == Reaction.DISLIKE,
                                ) { react(c, scope, d, mark, Reaction.DISLIKE) }
                                ToggleButton(
                                    if (mark.inWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    "Хочу посмотреть", mark.inWatchlist,
                                ) { scope.launch { c.repository.toggleWatchlist(t) } }
                                d.videos.firstOrNull()?.let { video ->
                                    Button(onClick = { openUrl("https://www.youtube.com/watch?v=${video.key}") }) {
                                        Icon(Icons.Filled.PlayArrow, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Трейлер")
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            ReleaseInfo(d)
                            d.tagline?.let {
                                Spacer(Modifier.height(16.dp))
                                Text("«$it»", fontWeight = FontWeight.Medium)
                            }
                            if (t.overview.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(t.overview, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (d.directors.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    (if (t.type == MediaType.MOVIE) "Режиссёр: " else "Создатели: ") + d.directors.joinToString(", "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Column(Modifier.offset(y = (-100).dp).padding(horizontal = 40.dp)) {
                        if (d.cast.isNotEmpty()) {
                            SectionTitle("В ролях")
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                d.cast.take(10).forEach { person ->
                                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.outline)
                                            AsyncImage(
                                                model = Images.profile(person.photoPath), contentDescription = null,
                                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        Text(person.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                                        person.character?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        if (d.similar.isNotEmpty()) {
                            SectionTitle("Похожее")
                            FittingRow(d.similar, cardWidth = 160.dp) { s, m ->
                                TitleCard(s, profile.matchPercent(s), marks[s.key], onClick = { nav.openTitle(s) }, modifier = m)
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        IconButton(
            onClick = nav::back,
            modifier = Modifier.padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
    }
}

private fun react(
    c: DesktopContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    d: TitleDetails,
    mark: UserMark,
    reaction: Reaction,
) {
    val next = if (mark.reaction == reaction) Reaction.NONE else reaction
    scope.launch { c.repository.setReaction(d.title, next) }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(onClick = onClick) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, colors = ButtonDefaults.outlinedButtonColors()) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun ReleaseInfo(d: TitleDetails) {
    val today = LocalDate.now()
    val lines = buildList {
        if (d.title.type == MediaType.MOVIE) {
            d.theatricalDate?.let { add((if (it.isAfter(today)) "Премьера в кино: " else "Вышел в кино: ") + it.formatFull()) }
            val digital = d.digitalDate
            when {
                digital != null -> add((if (digital.isAfter(today)) "Выйдет онлайн: " else "Доступен онлайн с ") + digital.formatFull())
                d.theatricalDate?.isAfter(today) == false -> add("Дата цифрового релиза пока неизвестна")
            }
        } else {
            d.title.date?.let { add((if (it.isAfter(today)) "Премьера: " else "Первый эпизод: ") + it.formatFull()) }
            d.seasons?.let { add("Сезонов: $it") }
            d.nextEpisode?.let { ep ->
                add("Следующая серия: ${ep.season} сезон, ${ep.number} эпизод" + (ep.date?.let { " — ${it.formatFull()}" } ?: ""))
            }
        }
    }
    if (lines.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun metaLine(d: TitleDetails): String {
    val parts = mutableListOf<String>()
    d.title.year?.let { parts += it.toString() }
    if (d.title.type == MediaType.TV) d.seasons?.let { parts += "сериал, сезонов: $it" }
    else d.runtimeMinutes?.let { m -> parts += if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин" }
    if (d.genres.isNotEmpty()) parts += d.genres.take(3).joinToString(", ")
    return parts.joinToString(" · ")
}

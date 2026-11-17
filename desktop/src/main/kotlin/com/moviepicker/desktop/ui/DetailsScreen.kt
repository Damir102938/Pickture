package com.moviepicker.desktop.ui

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.Event
import androidx.compose.ui.text.font.FontStyle
import com.moviepicker.desktop.ui.design.BackButton
import com.moviepicker.desktop.ui.design.Brand
import com.moviepicker.desktop.ui.design.MarqueeButton
import com.moviepicker.desktop.ui.design.PkType
import com.moviepicker.desktop.ui.design.SectionHeader
import com.moviepicker.desktop.ui.design.Stamp
import com.moviepicker.desktop.ui.design.StampButton
import com.moviepicker.desktop.ui.design.TicketField
import com.moviepicker.desktop.ui.design.TicketShape
import com.moviepicker.desktop.ui.design.VerticalPerforation
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
                        Poster(t.posterPath, Modifier.width(240.dp), size = "w500", shape = RoundedCornerShape(8.dp))
                        Spacer(Modifier.width(32.dp))
                        Column(Modifier.weight(1f).padding(top = 116.dp).widthIn(max = 900.dp)) {
                            if (d.genres.isNotEmpty()) {
                                Text(d.genres.take(3).joinToString(" · ").uppercase(), style = PkType.kicker, color = Brand.Gold)
                            }
                            Text(t.name.uppercase(), style = PkType.hero, color = Brand.Text)
                            t.originalName?.let { Text(it, color = Brand.TextMuted) }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                t.year?.let { TicketField("Год", it.toString()) }
                                if (t.type == MediaType.TV) d.seasons?.let { TicketField("Сезоны", it.toString()) }
                                else d.runtimeMinutes?.let { m -> TicketField("Хрон.", "%d:%02d".format(m / 60, m % 60)) }
                                if (t.voteCount > 0) TicketField("TMDB · ${t.voteCount}", "★ %.1f".format(t.rating), valueColor = Brand.Gold)
                                profile.matchPercent(t)?.let {
                                    Spacer(Modifier.width(8.dp))
                                    Stamp("Совпадение $it%", matchColor(it))
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                StampButton(
                                    if (mark.reaction == Reaction.LIKE) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    "Нравится", mark.reaction == Reaction.LIKE, Brand.Accent,
                                    onClick = { react(c, scope, d, mark, Reaction.LIKE) },
                                    modifier = Modifier.width(132.dp),
                                )
                                StampButton(
                                    if (mark.reaction == Reaction.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                    "Не моё", mark.reaction == Reaction.DISLIKE, Brand.MatchLow,
                                    onClick = { react(c, scope, d, mark, Reaction.DISLIKE) },
                                    modifier = Modifier.width(132.dp),
                                )
                                StampButton(
                                    if (mark.inWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    "В список", mark.inWatchlist, Brand.Gold,
                                    onClick = { scope.launch { c.repository.toggleWatchlist(t) } },
                                    modifier = Modifier.width(132.dp),
                                )
                                d.videos.firstOrNull()?.let { video ->
                                    Spacer(Modifier.width(6.dp))
                                    MarqueeButton(
                                        "Смотреть трейлер",
                                        { openUrl("https://www.youtube.com/watch?v=${video.key}") },
                                        icon = Icons.Filled.PlayArrow,
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            ReleaseInfo(d)
                            d.tagline?.let { raw ->
                                val tagline = raw.trim().trim('«', '»', '"', '“', '”', '„').trim()
                                if (tagline.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(Modifier.height(IntrinsicSize.Min)) {
                                        Box(Modifier.width(2.dp).fillMaxHeight().background(Brand.Gold))
                                        Spacer(Modifier.width(12.dp))
                                        Text("«$tagline»", fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
                                    }
                                }
                            }
                            if (t.overview.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(t.overview, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (d.directors.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    (if (t.type == MediaType.MOVIE) "Режиссёр: " else "Создатели: ") + d.directors.joinToString(", "),
                                    color = Brand.TextMuted,
                                )
                            }
                        }
                    }
                    Column(Modifier.offset(y = (-100).dp).padding(horizontal = 40.dp)) {
                        if (d.cast.isNotEmpty()) {
                            SectionHeader("В ролях")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                d.cast.take(10).forEach { person ->
                                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.width(96.dp).height(120.dp).clip(RoundedCornerShape(6.dp)).background(Brand.TicketHigh),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Person, null, tint = Brand.Perforation)
                                            AsyncImage(
                                                model = Images.profile(person.photoPath), contentDescription = null,
                                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(person.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                                        person.character?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                                color = Brand.TextMuted, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        if (d.similar.isNotEmpty()) {
                            SectionHeader("Похожее")
                            Spacer(Modifier.height(8.dp))
                            FittingRow(d.similar, cardWidth = 160.dp) { s, m ->
                                TitleCard(s, profile.matchPercent(s), marks[s.key], onClick = { nav.openTitle(s) }, modifier = m)
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        BackButton(nav::back, Modifier.padding(16.dp), onImage = true)
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

/** Даты выхода — отдельный билетик: слева корешок «РЕЛИЗ», справа строки. */
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
    val stub = 64.dp
    Row(
        Modifier.widthIn(max = 640.dp).height(IntrinsicSize.Min)
            .clip(TicketShape(notchRadius = 7.dp, cornerRadius = 8.dp, notchOffset = stub, vertical = true))
            .background(Brand.Ticket),
    ) {
        Column(
            Modifier.width(stub).fillMaxHeight().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Event, null, tint = Brand.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(3.dp))
            Text("РЕЛИЗ", style = PkType.tiny, color = Brand.TextMuted)
        }
        VerticalPerforation(inset = 8.dp)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

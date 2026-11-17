package com.moviepicker.ui.details

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Event
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.moviepicker.ui.design.BackButton
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.MarqueeButton
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.SectionHeader
import com.moviepicker.ui.design.Stamp
import com.moviepicker.ui.design.StampButton
import com.moviepicker.ui.design.TicketField
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.VerticalPerforation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.moviepicker.AppContainer
import com.moviepicker.domain.Images
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleDetails
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.ui.common.ErrorBox
import com.moviepicker.ui.common.LoadState
import com.moviepicker.ui.common.LoadingBox
import com.moviepicker.ui.common.Poster
import com.moviepicker.ui.common.TitleCard
import com.moviepicker.ui.common.appViewModel
import com.moviepicker.ui.common.formatFull
import com.moviepicker.ui.common.matchColor
import com.moviepicker.ui.common.userMessage
import com.moviepicker.ui.theme.Gold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DetailsViewModel(private val container: AppContainer, private val key: TitleKey) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TitleDetails>>(LoadState.Loading)
    val state = _state.asStateFlow()
    val mark = container.repository.observeMark(key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserMark())
    val profile = container.tasteProfile
    val marks = container.marks

    init {
        load()
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = runCatching { container.repository.details(key) }.fold(
                onSuccess = { LoadState.Success(it) },
                onFailure = { LoadState.Error(it.userMessage()) },
            )
        }
    }

    private val title: Title? get() = (_state.value as? LoadState.Success)?.data?.title

    fun react(reaction: Reaction) {
        val t = title ?: return
        val newReaction = if (mark.value.reaction == reaction) Reaction.NONE else reaction
        viewModelScope.launch { container.repository.setReaction(t, newReaction) }
    }

    fun toggleWatchlist() {
        val t = title ?: return
        viewModelScope.launch { container.repository.toggleWatchlist(t) }
    }
}

@Composable
fun DetailsScreen(
    key: TitleKey,
    onBack: () -> Unit,
    onOpen: (Title) -> Unit,
    onTrailer: (videoKey: String) -> Unit,
) {
    val vm = appViewModel(key = "${key.type.path}:${key.id}") { DetailsViewModel(it, key) }
    val state by vm.state.collectAsStateWithLifecycle()
    val mark by vm.mark.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Error -> ErrorBox(s.message, onRetry = vm::load, modifier = Modifier.align(Alignment.Center))
            is LoadState.Success -> DetailsContent(
                details = s.data,
                mark = mark,
                match = profile.matchPercent(s.data.title),
                matchOf = profile::matchPercent,
                marks = marks,
                onReact = vm::react,
                onWatchlist = vm::toggleWatchlist,
                onOpen = onOpen,
                onTrailer = onTrailer,
            )
        }
        BackButton(onBack, Modifier.statusBarsPadding().padding(10.dp), onImage = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(
    details: TitleDetails,
    mark: UserMark,
    match: Int?,
    matchOf: (Title) -> Int?,
    marks: Map<TitleKey, UserMark>,
    onReact: (Reaction) -> Unit,
    onWatchlist: () -> Unit,
    onOpen: (Title) -> Unit,
    onTrailer: (videoKey: String) -> Unit,
) {
    val t = details.title

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                AsyncImage(
                    model = Images.backdrop(t.backdropPath) ?: Images.poster(t.posterPath, "w780"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.5f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        )
                    )
                )
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-60).dp)) {
                Poster(t.posterPath, Modifier.width(108.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(top = 58.dp)) {
                    if (details.genres.isNotEmpty()) {
                        Text(
                            details.genres.take(3).joinToString(" · ").uppercase(),
                            style = PkType.kicker.copy(letterSpacing = 1.sp), color = Brand.Gold, maxLines = 2,
                        )
                    }
                    Text(t.name.uppercase(), style = PkType.screenTitle.copy(fontSize = 22.sp, lineHeight = 26.sp), color = Brand.Text)
                    t.originalName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
                    }
                }
            }
        }
        item {
            Column(Modifier.offset(y = (-44).dp).padding(horizontal = 16.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    t.year?.let { TicketField("Год", it.toString()) }
                    if (t.type == MediaType.TV) {
                        details.seasons?.let { TicketField("Сезоны", it.toString()) }
                    } else {
                        details.runtimeMinutes?.let { m -> TicketField("Хрон.", "%d:%02d".format(m / 60, m % 60)) }
                    }
                    if (t.voteCount > 0) TicketField("TMDB", "★ %.1f".format(t.rating), valueColor = Brand.Gold)
                    if (match != null) {
                        Spacer(Modifier.width(4.dp))
                        Stamp("Совпадение $match%", matchColor(match), Modifier.padding(top = 10.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampButton(
                        icon = if (mark.reaction == Reaction.LIKE) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = "Нравится",
                        selected = mark.reaction == Reaction.LIKE,
                        color = Brand.Accent,
                        onClick = { onReact(Reaction.LIKE) },
                        modifier = Modifier.weight(1f),
                    )
                    StampButton(
                        icon = if (mark.reaction == Reaction.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        label = "Не моё",
                        selected = mark.reaction == Reaction.DISLIKE,
                        color = Brand.MatchLow,
                        onClick = { onReact(Reaction.DISLIKE) },
                        modifier = Modifier.weight(1f),
                    )
                    StampButton(
                        icon = if (mark.inWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        label = "В список",
                        selected = mark.inWatchlist,
                        color = Brand.Gold,
                        onClick = onWatchlist,
                        modifier = Modifier.weight(1f),
                    )
                }
                details.videos.firstOrNull()?.let { trailer ->
                    Spacer(Modifier.height(12.dp))
                    val more = details.videos.size - 1
                    MarqueeButton(
                        if (more > 0) "Смотреть трейлер · ещё $more" else "Смотреть ${trailer.typeLabel.lowercase()}",
                        onClick = { onTrailer(trailer.key) },
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ReleaseInfo(details)
                details.tagline?.let { raw ->
                    val tagline = raw.trim().trim('«', '»', '"', '“', '”', '„').trim()
                    if (tagline.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(Brand.Gold))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "«$tagline»", style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (t.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(t.overview, style = MaterialTheme.typography.bodyMedium)
                }
                if (details.directors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        (if (t.type == MediaType.MOVIE) "Режиссёр: " else "Создатели: ") +
                            details.directors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextMuted,
                    )
                }
            }
        }
        if (details.cast.isNotEmpty()) {
            item {
                SectionHeader("В ролях", Modifier.offset(y = (-28).dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.offset(y = (-24).dp),
                ) {
                    items(details.cast, key = { it.id }) { person ->
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.width(72.dp).height(90.dp).clip(RoundedCornerShape(6.dp)).background(Brand.TicketHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Person, null, tint = Brand.Perforation)
                                AsyncImage(
                                    model = Images.profile(person.photoPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                person.name, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, maxLines = 2,
                            )
                            person.character?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    color = Brand.TextMuted, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (details.similar.isNotEmpty()) {
            item {
                SectionHeader("Похожее", Modifier.offset(y = (-12).dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.offset(y = (-8).dp),
                ) {
                    items(details.similar, key = { "${it.type.path}:${it.id}" }) { s ->
                        TitleCard(s, matchOf(s), marks[s.key], onClick = { onOpen(s) }, modifier = Modifier.width(112.dp))
                    }
                }
            }
        }
        item {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Даты выхода — отдельный билетик: слева корешок «РЕЛИЗ», справа строки. */
@Composable
private fun ReleaseInfo(details: TitleDetails) {
    val today = LocalDate.now()
    val lines = buildList {
        if (details.title.type == MediaType.MOVIE) {
            details.theatricalDate?.let {
                add((if (it.isAfter(today)) "Премьера в кино: " else "Вышел в кино: ") + it.formatFull())
            }
            val digital = details.digitalDate
            when {
                digital != null ->
                    add((if (digital.isAfter(today)) "Выйдет онлайн: " else "Доступен онлайн с ") + digital.formatFull())
                details.theatricalDate?.isAfter(today) == false -> add("Дата цифрового релиза пока неизвестна")
            }
        } else {
            details.status?.let { add("Статус: " + statusName(it)) }
            details.title.date?.let {
                add((if (it.isAfter(today)) "Премьера: " else "Первый эпизод: ") + it.formatFull())
            }
            details.nextEpisode?.let { ep ->
                add("Следующая серия: ${ep.season} сезон, ${ep.number} эпизод" + (ep.date?.let { " — ${it.formatFull()}" } ?: ""))
            }
        }
    }
    if (lines.isEmpty()) return
    val stub = 56.dp
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp, notchOffset = stub, vertical = true))
            .background(Brand.Ticket),
    ) {
        Column(
            Modifier.width(stub).fillMaxHeight().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Event, null, tint = Brand.Gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(3.dp))
            Text("РЕЛИЗ", style = PkType.tiny, color = Brand.TextMuted)
        }
        VerticalPerforation(inset = 8.dp)
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun statusName(status: String) = when (status) {
    "Returning Series" -> "продолжается"
    "Ended" -> "завершён"
    "Canceled" -> "закрыт"
    "In Production" -> "в производстве"
    "Planned" -> "запланирован"
    "Pilot" -> "пилот"
    else -> status
}

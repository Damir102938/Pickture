package com.moviepicker.ui.details

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
import androidx.compose.ui.platform.LocalUriHandler
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
fun DetailsScreen(key: TitleKey, onBack: () -> Unit, onOpen: (Title) -> Unit) {
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
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
        }
    }
}

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
) {
    val t = details.title
    val uriHandler = LocalUriHandler.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
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
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-56).dp)) {
                Poster(t.posterPath, Modifier.width(112.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(top = 60.dp)) {
                    Text(t.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    t.originalName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        metaLine(details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(Modifier.offset(y = (-40).dp).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t.voteCount > 0) {
                        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                        Text(
                            " %.1f".format(t.rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "  ${t.voteCount} оценок TMDB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    if (match != null) {
                        Text(
                            "Совпадение $match%",
                            style = MaterialTheme.typography.titleSmall,
                            color = matchColor(match),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionButton(
                        icon = if (mark.reaction == Reaction.LIKE) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = "Нравится",
                        selected = mark.reaction == Reaction.LIKE,
                        onClick = { onReact(Reaction.LIKE) },
                    )
                    ActionButton(
                        icon = if (mark.reaction == Reaction.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        label = "Не моё",
                        selected = mark.reaction == Reaction.DISLIKE,
                        onClick = { onReact(Reaction.DISLIKE) },
                    )
                    ActionButton(
                        icon = if (mark.inWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        label = "Хочу посмотреть",
                        selected = mark.inWatchlist,
                        onClick = onWatchlist,
                    )
                }
                details.trailerYoutubeKey?.let { trailerKey ->
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { uriHandler.openUri("https://www.youtube.com/watch?v=$trailerKey") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Смотреть трейлер")
                    }
                }
                Spacer(Modifier.height(16.dp))
                ReleaseInfo(details)
                details.tagline?.let {
                    Spacer(Modifier.height(16.dp))
                    Text("«$it»", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                if (t.overview.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(t.overview, style = MaterialTheme.typography.bodyMedium)
                }
                if (details.directors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        (if (t.type == MediaType.MOVIE) "Режиссёр: " else "Создатели: ") +
                            details.directors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (details.cast.isNotEmpty()) {
            item {
                SectionTitle("В ролях")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(details.cast, key = { it.id }) { person ->
                        Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.outline)
                                AsyncImage(
                                    model = Images.profile(person.photoPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                person.name, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, maxLines = 2,
                            )
                            person.character?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (details.similar.isNotEmpty()) {
            item {
                SectionTitle("Похожее")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
    )
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = color) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun metaLine(d: TitleDetails): String {
    val parts = mutableListOf<String>()
    d.title.year?.let { parts += it.toString() }
    if (d.title.type == MediaType.TV) {
        d.seasons?.let { parts += seasonsText(it) }
    } else {
        d.runtimeMinutes?.let { m -> parts += if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин" }
    }
    if (d.genres.isNotEmpty()) parts += d.genres.take(3).joinToString(", ")
    return parts.joinToString(" · ")
}

private fun seasonsText(n: Int): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11 -> "сезон"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "сезона"
        else -> "сезонов"
    }
    return "$n $word"
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

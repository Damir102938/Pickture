package com.moviepicker.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviepicker.desktop.DesktopContainer
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Recommendation
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Для вас ----------

private object RecsCache {
    var state by mutableStateOf<LoadState<List<Recommendation>>>(LoadState.Loading)
    var hidden by mutableStateOf<Set<TitleKey>>(emptySet())
    var tab by mutableStateOf(MediaType.MOVIE)
}

@Composable
fun RecsScreen(c: DesktopContainer, nav: Navigator) {
    val profile by c.tasteProfile.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean) {
        val hasData = RecsCache.state is LoadState.Success
        if (!hasData) RecsCache.state = LoadState.Loading
        refreshing = true
        scope.launch {
            runCatching { c.recommendations.recommend(force) }
                .onSuccess {
                    RecsCache.state = LoadState.Success(it)
                    RecsCache.hidden = emptySet()
                }
                .onFailure { if (!hasData) RecsCache.state = LoadState.Error(it.userMessage()) }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { load(force = false) }

    fun hide(t: Title) {
        RecsCache.hidden = RecsCache.hidden + t.key
    }

    Column(Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp, end = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Для вас", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Подборка на основе ваших оценок. Оценивайте прямо здесь — список станет точнее.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { load(force = true) }, enabled = !refreshing) { Icon(Icons.Filled.Refresh, "Обновить") }
        }
        Spacer(Modifier.height(16.dp))
        when (val s = RecsCache.state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Error -> ErrorBox(s.message, onRetry = { load(true) })
            is LoadState.Success -> {
                val visible = s.data.filter { it.title.key !in RecsCache.hidden }
                SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp)) {
                    MediaType.entries.forEachIndexed { index, type ->
                        val count = visible.count { it.title.type == type }
                        SegmentedButton(
                            selected = RecsCache.tab == type,
                            onClick = { RecsCache.tab = type },
                            shape = SegmentedButtonDefaults.itemShape(index, MediaType.entries.size),
                        ) { Text(if (type == MediaType.MOVIE) "Фильмы · $count" else "Сериалы · $count") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val items = visible.filter { it.title.type == RecsCache.tab }
                if (items.isEmpty()) {
                    if (profile.isEmpty) MessageBox(
                        "Пока не на что опереться",
                        "Выберите любимые жанры или поставьте лайки в ленте новинок.",
                        actionLabel = "Выбрать жанры",
                        onAction = { nav.open(Screen.Genres) },
                    ) else MessageBox("Здесь пока пусто", "Обновите список или оцените ещё несколько фильмов.")
                } else {
                    val gridState = rememberLazyGridState()
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(440.dp),
                            state = gridState,
                            contentPadding = PaddingValues(bottom = 32.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(items, key = { "${it.title.type.path}:${it.title.id}" }) { rec ->
                                RecommendationCard(
                                    rec,
                                    onOpen = { nav.openTitle(rec.title) },
                                    onLike = { hide(rec.title); scope.launch { c.repository.setReaction(rec.title, Reaction.LIKE) } },
                                    onDislike = { hide(rec.title); scope.launch { c.repository.setReaction(rec.title, Reaction.DISLIKE) } },
                                    onWatchlist = { hide(rec.title); scope.launch { c.repository.toggleWatchlist(rec.title) } },
                                )
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: Recommendation,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onWatchlist: () -> Unit,
) {
    val t = rec.title
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(12.dp)) {
            Poster(t.posterPath, Modifier.width(120.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rec.matchPercent?.let {
                        Text("Совпадение $it%", color = matchColor(it), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                    }
                    if (t.voteCount >= 5) {
                        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
                        Text(" %.1f".format(t.rating))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(rec.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row {
                    QuickAction(Icons.Filled.FavoriteBorder, "Нравится", onLike)
                    QuickAction(Icons.Outlined.ThumbDown, "Не моё", onDislike)
                    QuickAction(Icons.Filled.BookmarkBorder, "Хочу посмотреть", onWatchlist)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(icon, label) }
    }
}

// ---------- Поиск ----------

private object SearchCache {
    var query by mutableStateOf("")
    var items by mutableStateOf<List<Title>>(emptyList())
}

@Composable
fun SearchScreen(c: DesktopContainer, nav: Navigator) {
    val profile by c.tasteProfile.collectAsState()
    val marks by c.marks.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(SearchCache.query, retry) {
        val q = SearchCache.query.trim()
        delay(400)
        loading = true
        error = null
        runCatching { if (q.isEmpty()) c.repository.trending(1) else c.repository.search(q).items }
            .onSuccess { SearchCache.items = it }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp, end = 32.dp)) {
        OutlinedTextField(
            value = SearchCache.query,
            onValueChange = { SearchCache.query = it },
            placeholder = { Text("Фильм или сериал") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (SearchCache.query.isNotEmpty()) IconButton(onClick = { SearchCache.query = "" }) { Icon(Icons.Filled.Close, "Очистить") }
            },
            singleLine = true,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (SearchCache.query.isBlank()) "Популярное на этой неделе" else "Результаты поиска",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        when {
            error != null && SearchCache.items.isEmpty() -> ErrorBox(error!!, onRetry = { retry++ })
            loading && SearchCache.items.isEmpty() -> LoadingBox()
            SearchCache.items.isEmpty() -> MessageBox("Ничего не нашлось", "Попробуйте другое название")
            else -> TitleGrid(SearchCache.items, c, nav, profile::matchPercent, marks)
        }
    }
}

@Composable
private fun TitleGrid(
    items: List<Title>,
    c: DesktopContainer,
    nav: Navigator,
    matchOf: (Title) -> Int?,
    marks: Map<TitleKey, com.moviepicker.domain.UserMark>,
    state: LazyGridState = rememberLazyGridState(),
) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            state = state,
            contentPadding = PaddingValues(bottom = 32.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(items, key = { "${it.type.path}:${it.id}" }) { t ->
                TitleCard(t, matchOf(t), marks[t.key], onClick = { nav.openTitle(t) })
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

// ---------- Мои списки ----------

private enum class LibraryTab(val label: String, val empty: String) {
    WATCHLIST("Хочу посмотреть", "Добавляйте сюда фильмы, которые хотите посмотреть"),
    LIKED("Нравится", "Ставьте лайки — так рекомендации станут точнее"),
    DISLIKED("Не моё", "Здесь будет то, что вам не понравилось"),
}

private object LibraryCache {
    var tab by mutableStateOf(LibraryTab.WATCHLIST)
}

@Composable
fun LibraryScreen(c: DesktopContainer, nav: Navigator) {
    val entities by c.repository.observeAll().collectAsState(emptyList())
    val profile by c.tasteProfile.collectAsState()
    val marks by c.marks.collectAsState()

    Column(Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp, end = 32.dp)) {
        Text("Мои списки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 640.dp)) {
            LibraryTab.entries.forEachIndexed { index, tab ->
                val count = entities.count { it.matches(tab) }
                SegmentedButton(
                    selected = LibraryCache.tab == tab,
                    onClick = { LibraryCache.tab = tab },
                    shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size),
                ) { Text(if (count > 0) "${tab.label} · $count" else tab.label) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val items = entities.filter { it.matches(LibraryCache.tab) }.map { it.toTitle() }
        if (items.isEmpty()) MessageBox("Пока пусто", LibraryCache.tab.empty)
        else TitleGrid(items, c, nav, profile::matchPercent, marks)
    }
}

private fun com.moviepicker.data.local.UserTitleEntity.matches(tab: LibraryTab) = when (tab) {
    LibraryTab.WATCHLIST -> inWatchlist
    LibraryTab.LIKED -> reaction > 0
    LibraryTab.DISLIKED -> reaction < 0
}

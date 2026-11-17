package com.moviepicker.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import com.moviepicker.desktop.ui.design.Brand
import com.moviepicker.desktop.ui.design.PkType
import com.moviepicker.desktop.ui.design.ScreenHeader
import com.moviepicker.desktop.ui.design.SectionHeader
import com.moviepicker.desktop.ui.design.StampIconButton
import com.moviepicker.desktop.ui.design.TicketOutlineButton
import com.moviepicker.desktop.ui.design.TicketShape
import com.moviepicker.desktop.ui.design.TicketToggle
import com.moviepicker.desktop.ui.design.VerticalPerforation
import com.moviepicker.desktop.ui.design.pressClickable
import com.moviepicker.desktop.ui.design.ticketFieldColors
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
        ScreenHeader("Для вас", subtitle = "Подборка на основе ваших оценок. Оценивайте прямо здесь — список станет точнее.") {
            TicketOutlineButton("Обновить", { load(force = true) }, icon = Icons.Filled.Refresh, enabled = !refreshing)
        }
        Spacer(Modifier.height(16.dp))
        when (val s = RecsCache.state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Error -> ErrorBox(s.message, onRetry = { load(true) })
            is LoadState.Success -> {
                val visible = s.data.filter { it.title.key !in RecsCache.hidden }
                val types = MediaType.entries
                TicketToggle(
                    options = types.map { type ->
                        val count = visible.count { it.title.type == type }
                        if (type == MediaType.MOVIE) "Фильмы · $count" else "Сериалы · $count"
                    },
                    selected = types.indexOf(RecsCache.tab),
                    onSelect = { RecsCache.tab = types[it] },
                    modifier = Modifier.widthIn(max = 420.dp),
                )
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
                                    modifier = Modifier.animateItem(),
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

private val StubWidth = 64.dp

/** Рекомендация — билет: постер, описание, линия отрыва и корешок с процентом совпадения. */
@Composable
private fun RecommendationCard(
    rec: Recommendation,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = rec.title
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TicketShape(notchRadius = 7.dp, cornerRadius = 10.dp, notchOffset = StubWidth, fromEnd = true, vertical = true))
            .background(Brand.Ticket)
            .pressClickable(onClick = onOpen),
    ) {
        Poster(
            t.posterPath,
            Modifier.width(124.dp).fillMaxHeight().heightIn(min = 186.dp),
            shape = RoundedCornerShape(0.dp),
            ratio = null,
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.subtitle(), style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
                if (t.voteCount >= 5) Text("  ★ %.1f".format(t.rating), style = PkType.number, color = Brand.Gold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                rec.reason, style = MaterialTheme.typography.bodySmall, color = Brand.Accent,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Filled.FavoriteBorder, "Нравится", onLike, Brand.Accent)
                QuickAction(Icons.Outlined.ThumbDown, "Не моё", onDislike, Brand.Text)
                QuickAction(Icons.Filled.BookmarkBorder, "Хочу посмотреть", onWatchlist, Brand.Gold)
            }
        }
        VerticalPerforation(inset = 9.dp)
        Column(
            Modifier.width(StubWidth).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val match = rec.matchPercent
            if (match != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$match", style = PkType.screenTitle, color = matchColor(match))
                    Text("%", style = PkType.number, color = matchColor(match), modifier = Modifier.padding(bottom = 4.dp))
                }
            } else {
                Text("—", style = PkType.screenTitle, color = Brand.TextMuted)
            }
            Spacer(Modifier.height(4.dp))
            Text("СОВП.", style = PkType.tiny, color = Brand.TextMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        StampIconButton(icon, label, onClick, color = color)
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
        ScreenHeader("Поиск", kicker = "Pickture · касса")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = SearchCache.query,
            onValueChange = { SearchCache.query = it },
            placeholder = { Text("Фильм или сериал") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Brand.Gold) },
            shape = TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp),
            colors = ticketFieldColors(),
            trailingIcon = {
                if (SearchCache.query.isNotEmpty()) IconButton(onClick = { SearchCache.query = "" }) { Icon(Icons.Filled.Close, "Очистить") }
            },
            singleLine = true,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SectionHeader(if (SearchCache.query.isBlank()) "Популярное на этой неделе" else "Результаты поиска")
        Spacer(Modifier.height(8.dp))
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
            columns = GridCells.Adaptive(164.dp),
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
        ScreenHeader("Мои списки", kicker = "Pickture · мои билеты")
        Spacer(Modifier.height(12.dp))
        TicketToggle(
            options = LibraryTab.entries.map { tab ->
                val count = entities.count { it.matches(tab) }
                if (count > 0) "${tab.label} · $count" else tab.label
            },
            selected = LibraryCache.tab.ordinal,
            onSelect = { LibraryCache.tab = LibraryTab.entries[it] },
            modifier = Modifier.widthIn(max = 640.dp),
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = LibraryCache.tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it * dir / 6 } + fadeOut(tween(160)))
            },
            label = "libraryTab",
        ) { tab ->
            val items = entities.filter { it.matches(tab) }.map { it.toTitle() }
            if (items.isEmpty()) MessageBox("Пока пусто", tab.empty)
            else TitleGrid(items, c, nav, profile::matchPercent, marks)
        }
    }
}

private fun com.moviepicker.data.local.UserTitleEntity.matches(tab: LibraryTab) = when (tab) {
    LibraryTab.WATCHLIST -> inWatchlist
    LibraryTab.LIKED -> reaction > 0
    LibraryTab.DISLIKED -> reaction < 0
}

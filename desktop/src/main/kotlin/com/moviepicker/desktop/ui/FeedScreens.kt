package com.moviepicker.desktop.ui

import androidx.compose.foundation.lazy.itemsIndexed
import com.moviepicker.desktop.ui.design.MarqueeLoader
import com.moviepicker.desktop.ui.design.PicktureTopBar
import com.moviepicker.desktop.ui.design.ScreenHeader
import com.moviepicker.desktop.ui.design.SeatTag
import com.moviepicker.desktop.ui.design.SectionHeader
import com.moviepicker.desktop.ui.design.TextAction
import com.moviepicker.desktop.ui.design.TicketOutlineButton
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moviepicker.data.FeedSection
import com.moviepicker.desktop.DesktopContainer
import com.moviepicker.domain.Title
import kotlinx.coroutines.launch

/** Состояние ленты переживает переключение вкладок (держится, пока открыто окно). */
private object HomeCache {
    val rows = mutableStateMapOf<FeedSection, LoadState<List<Title>>>()
}

@Composable
fun HomeScreen(c: DesktopContainer, nav: Navigator) {
    val profile by c.tasteProfile.collectAsState()
    val marks by c.marks.collectAsState()
    val scope = rememberCoroutineScope()
    val rows = HomeCache.rows

    fun load() {
        FeedSection.entries.forEach { section ->
            rows[section] = LoadState.Loading
            scope.launch {
                rows[section] = runCatching { c.repository.feed(section).items }.fold(
                    onSuccess = { LoadState.Success(it) },
                    onFailure = { LoadState.Error(it.userMessage()) },
                )
            }
        }
    }
    LaunchedEffect(Unit) { if (rows.isEmpty()) load() }

    val forYou = remember(rows.toMap(), profile) {
        if (profile.isEmpty) emptyList()
        else rows.filterKeys { it != FeedSection.TRENDING }.values
            .flatMap { (it as? LoadState.Success)?.data.orEmpty() }
            .distinctBy { it.key }
            .filter { it.key !in profile.excluded }
            .mapNotNull { t -> profile.matchPercent(t)?.let { t to it } }
            .filter { it.second >= 70 }
            .sortedWith(compareByDescending<Pair<Title, Int>> { it.second }.thenByDescending { it.first.popularity })
            .map { it.first }
    }

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            item {
                ScreenHeader("Что нового", subtitle = "Свежие релизы, сериалы и анонсы") {
                    TicketOutlineButton("Обновить", ::load, icon = Icons.Filled.Refresh)
                }
            }
            val offset = if (forYou.isNotEmpty()) 1 else 0
            if (forYou.isNotEmpty()) item {
                FeedRow(1, "Новинки для вас", "Свежее, что совпадает с вашим вкусом", LoadState.Success(forYou), onAll = null, onRetry = {}) { t, m ->
                    TitleCard(t, profile.matchPercent(t), marks[t.key], onClick = { nav.openTitle(t) }, modifier = m)
                }
            }
            itemsIndexed(FeedSection.entries, key = { _, s -> s.name }) { index, section ->
                FeedRow(index + 1 + offset, section.title, section.subtitle, rows[section] ?: LoadState.Loading,
                    onAll = { nav.open(Screen.Section(section)) }, onRetry = ::load) { t, m ->
                    TitleCard(t, profile.matchPercent(t), marks[t.key], onClick = { nav.openTitle(t) }, modifier = m)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun FeedRow(
    number: Int,
    title: String,
    subtitle: String,
    state: LoadState<List<Title>>,
    onAll: (() -> Unit)?,
    onRetry: () -> Unit,
    card: @Composable (Title, Modifier) -> Unit,
) {
    Column {
        SectionHeader(title, number = number, subtitle = subtitle, onAll = onAll)
        Spacer(Modifier.height(8.dp))
        when (state) {
            LoadState.Loading -> LoadingBox(height = 280.dp)
            is LoadState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextAction("Повторить", onRetry)
            }
            is LoadState.Success ->
                if (state.data.isEmpty()) Text("Пока ничего нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FittingRow(state.data, content = card)
        }
    }
}

private enum class SortMode(val label: String) { POPULAR("Популярные"), MATCH("По вкусу"), DATE("По дате") }

@Composable
fun SectionScreen(c: DesktopContainer, nav: Navigator, section: FeedSection) {
    val profile by c.tasteProfile.collectAsState()
    val marks by c.marks.collectAsState()
    val scope = rememberCoroutineScope()
    var items by remember(section) { mutableStateOf(emptyList<Title>()) }
    var page by remember(section) { mutableStateOf(0) }
    var hasMore by remember(section) { mutableStateOf(true) }
    var loading by remember(section) { mutableStateOf(false) }
    var error by remember(section) { mutableStateOf<String?>(null) }
    var sort by remember(section) { mutableStateOf(SortMode.POPULAR) }

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        scope.launch {
            runCatching { c.repository.feed(section, page + 1) }
                .onSuccess {
                    items = (items + it.items).distinctBy { t -> t.key }
                    page = it.page
                    hasMore = it.hasMore
                }
                .onFailure { error = it.userMessage() }
            loading = false
        }
    }
    LaunchedEffect(section) { loadMore() }

    val sorted = when (sort) {
        SortMode.POPULAR -> items
        SortMode.MATCH -> items.sortedByDescending { profile.matchPercent(it) ?: 0 }
        SortMode.DATE -> if (section == FeedSection.UPCOMING_MOVIES || section == FeedSection.UPCOMING_SERIES)
            items.sortedBy { it.date } else items.sortedByDescending { it.date }
    }
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd) loadMore() }

    Column(Modifier.fillMaxSize()) {
        PicktureTopBar(section.title, onBack = nav::back, modifier = Modifier.padding(start = 20.dp, end = 32.dp, top = 12.dp)) {
            SortMode.entries.forEach { mode ->
                if (mode == SortMode.MATCH && profile.isEmpty) return@forEach
                SeatTag(mode.label, selected = sort == mode, onClick = { sort = mode }, modifier = Modifier.padding(start = 8.dp))
            }
        }
        when {
            items.isEmpty() && error != null -> ErrorBox(error!!, onRetry = ::loadMore)
            items.isEmpty() && loading -> LoadingBox()
            else -> Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(164.dp),
                    state = gridState,
                    contentPadding = PaddingValues(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(sorted, key = { "${it.type.path}:${it.id}" }) { t ->
                        TitleCard(t, profile.matchPercent(t), marks[t.key], onClick = { nav.openTitle(t) })
                    }
                    if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            MarqueeLoader()
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

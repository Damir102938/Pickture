package com.moviepicker.ui.recs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.moviepicker.domain.MediaType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moviepicker.AppContainer
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Recommendation
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.ui.common.ErrorBox
import com.moviepicker.ui.common.LoadState
import com.moviepicker.ui.common.LoadingBox
import com.moviepicker.ui.common.MessageBox
import com.moviepicker.ui.common.Poster
import com.moviepicker.ui.common.appViewModel
import com.moviepicker.ui.common.matchColor
import com.moviepicker.ui.common.subtitle
import com.moviepicker.ui.common.userMessage
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.ScreenHeader
import com.moviepicker.ui.design.StampIconButton
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.TicketToggle
import com.moviepicker.ui.design.VerticalPerforation
import com.moviepicker.ui.design.pressClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<List<Recommendation>>>(LoadState.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Оценённые прямо в списке — скрываем сразу, не дожидаясь пересчёта. */
    private val _hidden = MutableStateFlow<Set<TitleKey>>(emptySet())
    val hidden = _hidden.asStateFlow()
    val profile = container.tasteProfile

    fun load(force: Boolean) {
        viewModelScope.launch {
            val hasData = _state.value is LoadState.Success
            if (hasData) _refreshing.value = true else _state.value = LoadState.Loading
            runCatching { container.recommendations.recommend(force) }
                .onSuccess {
                    _state.value = LoadState.Success(it)
                    _hidden.value = emptySet()
                }
                .onFailure { if (!hasData) _state.value = LoadState.Error(it.userMessage()) }
            _refreshing.value = false
        }
    }

    fun react(title: Title, reaction: Reaction) {
        _hidden.update { it + title.key }
        viewModelScope.launch { container.repository.setReaction(title, reaction) }
    }

    fun addToWatchlist(title: Title) {
        _hidden.update { it + title.key }
        viewModelScope.launch { container.repository.toggleWatchlist(title) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(onOpen: (Title) -> Unit, onSetupTaste: () -> Unit) {
    val vm = appViewModel { RecsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MediaType.MOVIE) }

    // При каждом возвращении на вкладку — пересчёт, если оценки изменились (иначе берётся кэш)
    LaunchedEffect(Unit) { vm.load(force = false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.load(force = true) },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        when (val s = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Error -> ErrorBox(s.message, onRetry = { vm.load(true) })
            is LoadState.Success -> {
                val items = s.data.filter { it.title.key !in hidden && it.title.type == tab }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column {
                            ScreenHeader(
                                "Для вас",
                                subtitle = "Подборка по вашим оценкам. Оценивайте прямо здесь — список станет точнее.",
                            )
                            Spacer(Modifier.height(8.dp))
                            // Фильмы и сериалы — отдельными вкладками, чтобы не путались
                            val types = MediaType.entries
                            TicketToggle(
                                options = types.map { type ->
                                    val count = s.data.count { it.title.type == type && it.title.key !in hidden }
                                    (if (type == MediaType.MOVIE) "Фильмы · " else "Сериалы · ") + count
                                },
                                selected = types.indexOf(tab),
                                onSelect = { tab = types[it] },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    if (items.isEmpty()) {
                        item {
                            if (profile.isEmpty) {
                                MessageBox(
                                    "Пока не на что опереться",
                                    "Отметьте любимые жанры и пару фильмов или поставьте лайки в ленте новинок.",
                                    actionLabel = "Выбрать жанры и фильмы",
                                    onAction = onSetupTaste,
                                )
                            } else {
                                MessageBox(
                                    "Рекомендации закончились",
                                    "Потяните вниз, чтобы обновить, или оцените ещё несколько фильмов.",
                                )
                            }
                        }
                    }
                    items(items, key = { "${it.title.type.path}:${it.title.id}" }) { rec ->
                        RecommendationRow(
                            rec = rec,
                            onOpen = { onOpen(rec.title) },
                            onLike = { vm.react(rec.title, Reaction.LIKE) },
                            onDislike = { vm.react(rec.title, Reaction.DISLIKE) },
                            onWatchlist = { vm.addToWatchlist(rec.title) },
                            modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

private val StubWidth = 54.dp

/** Рекомендация — билет: постер, описание, линия отрыва и корешок с процентом совпадения. */
@Composable
private fun RecommendationRow(
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
            .clip(TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp, notchOffset = StubWidth, fromEnd = true, vertical = true))
            .background(Brand.Ticket)
            .pressClickable(onClick = onOpen),
    ) {
        Poster(
            t.posterPath,
            Modifier.width(92.dp).fillMaxHeight().heightIn(min = 138.dp),
            shape = RoundedCornerShape(0.dp),
            ratio = null,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.subtitle(), style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
                if (t.voteCount >= 5) {
                    Text("  ★ %.1f".format(t.rating), style = PkType.number, color = Brand.Gold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                rec.reason, style = MaterialTheme.typography.bodySmall,
                color = Brand.Accent, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StampIconButton(Icons.Filled.FavoriteBorder, "Нравится", onLike, color = Brand.Accent)
                StampIconButton(Icons.Outlined.ThumbDown, "Не моё", onDislike)
                StampIconButton(Icons.Filled.BookmarkBorder, "Хочу посмотреть", onWatchlist, color = Brand.Gold)
            }
        }
        VerticalPerforation(inset = 8.dp)
        Column(
            Modifier.width(StubWidth).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val match = rec.matchPercent
            if (match != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$match", style = PkType.screenTitle, color = matchColor(match))
                    Text("%", style = PkType.number, color = matchColor(match), modifier = Modifier.padding(bottom = 3.dp))
                }
            } else {
                Text("—", style = PkType.screenTitle, color = Brand.TextMuted)
            }
            Spacer(Modifier.height(4.dp))
            Text("СОВП.", style = PkType.tiny, color = Brand.TextMuted)
        }
    }
}

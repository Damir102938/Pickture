package com.moviepicker.ui.recs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.moviepicker.ui.theme.Gold
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
                val items = s.data.filter { it.title.key !in hidden }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Column {
                            Text("Для вас", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Подборка на основе ваших оценок. Оценивайте прямо здесь — список будет точнее.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationRow(
    rec: Recommendation,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onWatchlist: () -> Unit,
) {
    val t = rec.title
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Poster(t.posterPath, Modifier.width(96.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(t.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                rec.matchPercent?.let {
                    Text(
                        "Совпадение $it%", style = MaterialTheme.typography.labelLarge,
                        color = matchColor(it), fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                if (t.voteCount >= 5) {
                    Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(14.dp))
                    Text(" %.1f".format(t.rating), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                rec.reason, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row {
                IconButton(onClick = onLike) { Icon(Icons.Filled.FavoriteBorder, "Нравится") }
                IconButton(onClick = onDislike) { Icon(Icons.Outlined.ThumbDown, "Не моё") }
                IconButton(onClick = onWatchlist) { Icon(Icons.Filled.BookmarkBorder, "Хочу посмотреть") }
            }
        }
    }
}

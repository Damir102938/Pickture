package com.moviepicker.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moviepicker.AppContainer
import com.moviepicker.data.FeedSection
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.ui.common.ErrorBox
import com.moviepicker.ui.common.LoadState
import com.moviepicker.ui.common.LoadingBox
import com.moviepicker.ui.common.TitleCard
import com.moviepicker.ui.common.appViewModel
import com.moviepicker.ui.common.userMessage
import com.moviepicker.ui.design.ScreenHeader
import com.moviepicker.ui.design.SectionHeader
import com.moviepicker.ui.design.TextAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val sections = FeedSection.entries

    private val _rows = MutableStateFlow<Map<FeedSection, LoadState<List<Title>>>>(
        sections.associateWith { LoadState.Loading }
    )
    val rows: StateFlow<Map<FeedSection, LoadState<List<Title>>>> = _rows.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    val profile = container.tasteProfile
    val marks = container.marks

    /** Свежие релизы и анонсы, которые лучше всего совпадают со вкусом. */
    val forYou: StateFlow<List<Title>> = combine(_rows, container.tasteProfile) { rows, profile ->
        if (profile.isEmpty) return@combine emptyList<Title>()
        rows.filterKeys { it != FeedSection.TRENDING }.values
            .flatMap { (it as? LoadState.Success)?.data.orEmpty() }
            .distinctBy { it.key }
            .filter { it.key !in profile.excluded }
            .mapNotNull { t -> profile.matchPercent(t)?.let { t to it } }
            .filter { it.second >= 70 }
            .sortedWith(compareByDescending<Pair<Title, Int>> { it.second }.thenByDescending { it.first.popularity })
            .take(20)
            .map { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load(refresh = false)
    }

    fun load(refresh: Boolean) {
        viewModelScope.launch {
            if (refresh) _refreshing.value = true
            else _rows.value = sections.associateWith { LoadState.Loading }
            sections.map { section ->
                launch {
                    val state = runCatching { container.repository.feed(section).items }.fold(
                        onSuccess = { LoadState.Success(it) },
                        onFailure = { LoadState.Error(it.userMessage()) },
                    )
                    _rows.update { it + (section to state) }
                }
            }.joinAll()
            _refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Title) -> Unit, onSection: (FeedSection) -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val forYou by vm.forYou.collectAsStateWithLifecycle()

    val allFailed = rows.values.all { it is LoadState.Error }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.load(refresh = true) },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        if (allFailed) {
            ErrorBox((rows.values.first() as LoadState.Error).message, onRetry = { vm.load(false) })
            return@PullToRefreshBox
        }
        // Разделы нумеруются по порядку, как сеансы в афише
        val offset = if (forYou.isNotEmpty()) 1 else 0
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                ScreenHeader("Что нового", subtitle = "Свежие релизы, сериалы и анонсы")
            }
            if (forYou.isNotEmpty()) {
                item {
                    TitleRow(
                        number = 1,
                        title = "Новинки для вас",
                        subtitle = "Свежее, что совпадает с вашим вкусом",
                        state = LoadState.Success(forYou),
                        matchOf = profile::matchPercent,
                        marks = marks,
                        onOpen = onOpen,
                        onAll = null,
                        onRetry = {},
                    )
                }
            }
            itemsIndexed(vm.sections, key = { _, s -> s.name }) { index, section ->
                TitleRow(
                    number = index + 1 + offset,
                    title = section.title,
                    subtitle = section.subtitle,
                    state = rows[section] ?: LoadState.Loading,
                    matchOf = profile::matchPercent,
                    marks = marks,
                    onOpen = onOpen,
                    onAll = { onSection(section) },
                    onRetry = { vm.load(false) },
                )
            }
        }
    }
}

@Composable
private fun TitleRow(
    number: Int,
    title: String,
    subtitle: String,
    state: LoadState<List<Title>>,
    matchOf: (Title) -> Int?,
    marks: Map<TitleKey, UserMark>,
    onOpen: (Title) -> Unit,
    onAll: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    Column(Modifier.padding(top = 14.dp)) {
        SectionHeader(title, number = number, subtitle = subtitle, onAll = onAll)
        Spacer(Modifier.height(6.dp))
        when (state) {
            LoadState.Loading -> LoadingBox(height = 220.dp)
            is LoadState.Error -> Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextAction("Повторить", onRetry)
            }
            is LoadState.Success -> if (state.data.isEmpty()) {
                Text(
                    "Пока ничего нет",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.data, key = { "${it.type.path}:${it.id}" }) { t ->
                        TitleCard(t, matchOf(t), marks[t.key], onClick = { onOpen(t) }, modifier = Modifier.width(118.dp))
                    }
                }
            }
        }
    }
}

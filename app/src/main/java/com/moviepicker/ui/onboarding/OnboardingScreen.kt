package com.moviepicker.ui.onboarding

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.Role
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.MarqueeButton
import com.moviepicker.ui.design.Perforation
import com.moviepicker.ui.design.ScreenHeader
import com.moviepicker.ui.design.SeatTag
import com.moviepicker.ui.design.Stamp
import com.moviepicker.ui.design.TextAction
import com.moviepicker.ui.design.pressClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moviepicker.AppContainer
import com.moviepicker.domain.Genres
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.ui.common.ErrorBox
import com.moviepicker.ui.common.LoadState
import com.moviepicker.ui.common.LoadingBox
import com.moviepicker.ui.common.Poster
import com.moviepicker.ui.common.appViewModel
import com.moviepicker.ui.common.rememberBackupActions
import com.moviepicker.ui.common.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val step: Int = 0,
    val genres: Set<Int> = emptySet(),
    val candidates: LoadState<List<Title>> = LoadState.Loading,
    val selected: Map<TitleKey, Title> = emptyMap(),
    val saving: Boolean = false,
)

class OnboardingViewModel(private val container: AppContainer, private val edit: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    init {
        if (edit) viewModelScope.launch {
            // При повторном прохождении показываем прошлый выбор
            val settings = container.settings.current()
            val liked = container.repository.allMarked()
                .filter { it.fromOnboarding && it.reaction > 0 }
                .map { it.toTitle() }
            _state.update { s -> s.copy(genres = settings.favoriteGenres, selected = liked.associateBy { it.key }) }
        }
    }

    fun toggleGenre(id: Int) = _state.update {
        it.copy(genres = if (id in it.genres) it.genres - id else it.genres + id)
    }

    fun toggleTitle(title: Title) = _state.update {
        it.copy(selected = if (title.key in it.selected) it.selected - title.key else it.selected + (title.key to title))
    }

    fun toStep(step: Int) {
        _state.update { it.copy(step = step) }
        if (step == 1) loadCandidates()
    }

    fun loadCandidates() {
        val genres = _state.value.genres
        _state.update { it.copy(candidates = LoadState.Loading) }
        viewModelScope.launch {
            val result = runCatching {
                val repo = container.repository
                if (genres.isEmpty()) {
                    repo.trending(1) + repo.trending(2)
                } else {
                    val movieGenres = genres.joinToString("|")
                    val tvGenres = Genres.toTv(genres)
                    val movies1 = async { repo.discover(MediaType.MOVIE, mapOf("with_genres" to movieGenres, "vote_count.gte" to "1500")) }
                    val movies2 = async { repo.discover(MediaType.MOVIE, mapOf("with_genres" to movieGenres, "vote_count.gte" to "1500"), page = 2) }
                    val tv = async {
                        if (tvGenres.isEmpty()) emptyList()
                        else repo.discover(MediaType.TV, mapOf("with_genres" to tvGenres.joinToString("|"), "vote_count.gte" to "500"))
                    }
                    interleave(movies1.await() + movies2.await(), tv.await())
                }
            }
            _state.update { s ->
                s.copy(
                    candidates = result.fold(
                        // Ранее выбранные — в начало, чтобы их было видно при редактировании
                        onSuccess = { list -> LoadState.Success((s.selected.values + list).distinctBy { it.key }) },
                        onFailure = { LoadState.Error(it.userMessage()) },
                    )
                )
            }
        }
    }

    private fun interleave(movies: List<Title>, tv: List<Title>): List<Title> {
        val result = mutableListOf<Title>()
        val m = movies.iterator()
        val t = tv.iterator()
        while (m.hasNext() || t.hasNext()) {
            repeat(2) { if (m.hasNext()) result += m.next() }
            if (t.hasNext()) result += t.next()
        }
        return result
    }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            container.repository.applyOnboarding(s.genres, s.selected.values.toList())
            container.recommendations.invalidate()
            onDone()
        }
    }

    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            // При первом запуске просто отмечаем онбординг пройденным, ничего не сохраняя
            if (!edit) container.settings.completeOnboarding(emptySet())
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(edit: Boolean, onDone: () -> Unit) {
    val vm = appViewModel(key = "onboarding:$edit") { OnboardingViewModel(it, edit) }
    val state by vm.state.collectAsStateWithLifecycle()
    // После восстановления из файла онбординг уже не нужен
    val backupActions = rememberBackupActions(onImported = { onDone() })

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            if (state.step == 0) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ScreenHeader(
                        "Что вы любите смотреть?",
                        kicker = "Шаг 1 из 2 · жанры",
                        subtitle = "Выберите жанры, и рекомендации появятся сразу. " +
                            "Выбор можно изменить или совсем отключить в настройках.",
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Genres.selectable.forEach { g ->
                            SeatTag(g.name, selected = g.movieId in state.genres, onClick = { vm.toggleGenre(g.movieId) })
                        }
                    }
                    if (!edit) {
                        Spacer(Modifier.height(20.dp))
                        TextAction(
                            "У меня есть резервная копия — восстановить",
                            backupActions.import,
                            enabled = !backupActions.busy,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                when (val c = state.candidates) {
                    LoadState.Loading -> LoadingBox()
                    is LoadState.Error -> ErrorBox(c.message, onRetry = vm::loadCandidates)
                    is LoadState.Success -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ScreenHeader(
                                "Что из этого понравилось?",
                                kicker = "Шаг 2 из 2 · фильмы",
                                subtitle = "Отметьте несколько — чем больше, тем точнее подборка.",
                                modifier = Modifier.offset(x = (-16).dp),
                            )
                        }
                        items(c.data, key = { "${it.type.path}:${it.id}" }) { t ->
                            SelectablePoster(t, t.key in state.selected) { vm.toggleTitle(t) }
                        }
                    }
                }
            }
        }
        Perforation(Modifier.padding(horizontal = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step == 0) {
                TextAction(if (edit) "Отмена" else "Пропустить", { vm.skip(onDone) }, color = Brand.TextMuted)
                Spacer(Modifier.weight(1f))
                MarqueeButton("Далее", { vm.toStep(1) })
            } else {
                TextAction("Назад", { vm.toStep(0) }, color = Brand.TextMuted)
                Spacer(Modifier.weight(1f))
                MarqueeButton(
                    if (state.selected.isEmpty()) "Готово" else "Готово · ${state.selected.size}",
                    { vm.finish(onDone) },
                    enabled = !state.saving,
                    loading = state.saving,
                )
            }
        }
    }
}

@Composable
private fun SelectablePoster(title: Title, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.pressClickable(role = Role.Checkbox, onClick = onClick)) {
        Box {
            Poster(
                title.posterPath,
                Modifier.fillMaxWidth().then(
                    if (selected) Modifier.border(BorderStroke(2.dp, Brand.Gold), RoundedCornerShape(6.dp))
                    else Modifier
                ),
            )
            if (selected) {
                Stamp("Моё", Brand.Gold, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f)))
            }
        }
        Text(
            title.name, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

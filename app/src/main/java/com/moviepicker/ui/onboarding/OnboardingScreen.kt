package com.moviepicker.ui.onboarding

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
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text("Что вы любите смотреть?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выберите жанры, и рекомендации появятся сразу. " +
                            "Выбор можно изменить или совсем отключить в настройках.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Genres.selectable.forEach { g ->
                            FilterChip(
                                selected = g.movieId in state.genres,
                                onClick = { vm.toggleGenre(g.movieId) },
                                label = { Text(g.name) },
                            )
                        }
                    }
                    if (!edit) {
                        Spacer(Modifier.height(24.dp))
                        TextButton(onClick = backupActions.import, enabled = !backupActions.busy) {
                            Text("У меня есть резервная копия — восстановить")
                        }
                    }
                }
            } else {
                when (val c = state.candidates) {
                    LoadState.Loading -> LoadingBox()
                    is LoadState.Error -> ErrorBox(c.message, onRetry = vm::loadCandidates)
                    is LoadState.Success -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    "Что из этого вам понравилось?",
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Отметьте несколько — чем больше, тем точнее подборка.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(c.data, key = { "${it.type.path}:${it.id}" }) { t ->
                            SelectablePoster(t, t.key in state.selected) { vm.toggleTitle(t) }
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step == 0) {
                    TextButton(onClick = { vm.skip(onDone) }) { Text(if (edit) "Отмена" else "Пропустить") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.toStep(1) }) { Text("Далее") }
                } else {
                    TextButton(onClick = { vm.toStep(0) }) { Text("Назад") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.finish(onDone) }, enabled = !state.saving) {
                        Text(if (state.selected.isEmpty()) "Готово" else "Готово · ${state.selected.size}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectablePoster(title: Title, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            Poster(
                title.posterPath,
                Modifier.fillMaxWidth().then(
                    if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
                    else Modifier
                ),
            )
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
        Text(
            title.name, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

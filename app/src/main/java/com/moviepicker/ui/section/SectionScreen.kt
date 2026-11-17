package com.moviepicker.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import com.moviepicker.ui.design.PicktureTopBar
import com.moviepicker.ui.design.SeatTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moviepicker.AppContainer
import com.moviepicker.data.FeedSection
import com.moviepicker.domain.Title
import com.moviepicker.ui.common.ErrorBox
import com.moviepicker.ui.common.LoadingBox
import com.moviepicker.ui.common.TitleGrid
import com.moviepicker.ui.common.appViewModel
import com.moviepicker.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortMode(val label: String) { POPULAR("Популярные"), MATCH("По вкусу"), DATE("По дате") }

data class SectionState(
    val items: List<Title> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val sort: SortMode = SortMode.POPULAR,
)

class SectionViewModel(private val container: AppContainer, val section: FeedSection) : ViewModel() {
    private val _state = MutableStateFlow(SectionState())
    val state = _state.asStateFlow()
    val profile = container.tasteProfile
    val marks = container.marks

    init {
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { container.repository.feed(section, s.page + 1) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = (it.items + page.items).distinctBy { t -> t.key },
                            page = page.page,
                            hasMore = page.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun setSort(mode: SortMode) = _state.update { it.copy(sort = mode) }
}

@Composable
fun SectionScreen(section: FeedSection, onBack: () -> Unit, onOpen: (Title) -> Unit) {
    val vm = appViewModel(key = section.name) { SectionViewModel(it, section) }
    val state by vm.state.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()

    val sorted = when (state.sort) {
        SortMode.POPULAR -> state.items
        SortMode.MATCH -> state.items.sortedByDescending { profile.matchPercent(it) ?: 0 }
        SortMode.DATE -> {
            val upcoming = section == FeedSection.UPCOMING_MOVIES || section == FeedSection.UPCOMING_SERIES
            if (upcoming) state.items.sortedBy { it.date } else state.items.sortedByDescending { it.date }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PicktureTopBar(section.title, onBack = onBack)
        when {
            state.items.isEmpty() && state.error != null -> ErrorBox(state.error!!, onRetry = vm::loadMore)
            state.items.isEmpty() && state.loading -> LoadingBox()
            else -> TitleGrid(
                items = sorted,
                matchOf = profile::matchPercent,
                markOf = { marks[it] },
                onOpen = onOpen,
                loadingMore = state.loading,
                canLoadMore = state.hasMore && state.error == null,
                onLoadMore = vm::loadMore,
                header = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortMode.entries.forEach { mode ->
                            if (mode == SortMode.MATCH && profile.isEmpty) return@forEach
                            SeatTag(mode.label, selected = state.sort == mode, onClick = { vm.setSort(mode) })
                        }
                    }
                },
            )
        }
    }
}

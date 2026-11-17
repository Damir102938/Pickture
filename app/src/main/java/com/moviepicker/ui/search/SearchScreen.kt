package com.moviepicker.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.Perforation
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.ScreenHeader
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.ticketFieldColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moviepicker.AppContainer
import com.moviepicker.domain.Title
import com.moviepicker.ui.common.ErrorBox
import com.moviepicker.ui.common.LoadingBox
import com.moviepicker.ui.common.MessageBox
import com.moviepicker.ui.common.TitleGrid
import com.moviepicker.ui.common.appViewModel
import com.moviepicker.ui.common.userMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val items: List<Title> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** Результаты относятся к поиску (а не к трендам, которые показываются при пустом запросе). */
    val isSearch: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private val queries = MutableStateFlow("")
    private var job: Job? = null
    val profile = container.tasteProfile
    val marks = container.marks

    init {
        viewModelScope.launch {
            queries.debounce(400).distinctUntilChanged().collect { load(it.trim(), page = 1) }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        queries.value = q
    }

    fun loadMore() {
        val s = _state.value
        if (!s.loading && s.hasMore) load(s.query.trim(), s.page + 1)
    }

    fun retry() = load(_state.value.query.trim(), 1)

    private fun load(query: String, page: Int) {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            runCatching {
                if (query.isEmpty()) {
                    val items = container.repository.trending(page)
                    com.moviepicker.data.Page(items, page, hasMore = page < 5)
                } else {
                    container.repository.search(query, page)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        items = if (page == 1) result.items else (it.items + result.items).distinctBy { t -> t.key },
                        page = result.page,
                        hasMore = result.hasMore,
                        loading = false,
                        isSearch = query.isNotEmpty(),
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }
}

@Composable
fun SearchScreen(onOpen: (Title) -> Unit) {
    val vm = appViewModel { SearchViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Поиск")
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            placeholder = { Text("Фильм или сериал") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Brand.Gold) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, "Очистить") }
                }
            },
            singleLine = true,
            shape = TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp),
            colors = ticketFieldColors(),
        )
        when {
            state.items.isEmpty() && state.error != null -> ErrorBox(state.error!!, onRetry = vm::retry)
            state.items.isEmpty() && state.loading -> LoadingBox()
            state.items.isEmpty() && state.isSearch -> MessageBox("Ничего не нашлось", "Попробуйте другое название")
            else -> TitleGrid(
                items = state.items,
                matchOf = profile::matchPercent,
                markOf = { marks[it] },
                onOpen = onOpen,
                loadingMore = state.loading && state.items.isNotEmpty(),
                canLoadMore = state.hasMore && state.error == null,
                onLoadMore = vm::loadMore,
                header = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (state.isSearch) "Результаты поиска" else "Популярное на этой неделе").uppercase(),
                            style = PkType.section,
                        )
                        Spacer(Modifier.width(10.dp))
                        Perforation(Modifier.weight(1f))
                    }
                },
            )
        }
    }
}

package com.moviepicker.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviepicker.container
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.domain.Title
import com.moviepicker.ui.common.MessageBox
import com.moviepicker.ui.common.TitleGrid

private enum class LibraryTab(val label: String, val empty: String, val filter: (UserTitleEntity) -> Boolean) {
    WATCHLIST("Хочу посмотреть", "Добавляйте сюда фильмы, и мы сообщим, когда они выйдут", { it.inWatchlist }),
    LIKED("Нравится", "Ставьте лайки — так рекомендации станут точнее", { it.reaction > 0 }),
    DISLIKED("Не моё", "Здесь будет то, что вам не понравилось", { it.reaction < 0 }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Title) -> Unit, onSettings: () -> Unit) {
    val container = LocalContext.current.container
    val entities by container.repository.observeAll().collectAsStateWithLifecycle(emptyList())
    val profile by container.tasteProfile.collectAsStateWithLifecycle()
    val marks by container.marks.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = LibraryTab.entries[tabIndex]
    val items = remember(entities, tab) { entities.filter(tab.filter).map { it.toTitle() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Мои списки") },
            actions = {
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки") }
            },
        )
        TabRow(selectedTabIndex = tabIndex) {
            LibraryTab.entries.forEachIndexed { i, t ->
                val count = entities.count(t.filter)
                Tab(
                    selected = i == tabIndex,
                    onClick = { tabIndex = i },
                    text = { Text(if (count > 0) "${t.label} · $count" else t.label, maxLines = 1) },
                )
            }
        }
        if (items.isEmpty()) {
            MessageBox("Пока пусто", tab.empty)
        } else {
            TitleGrid(
                items = items,
                matchOf = profile::matchPercent,
                markOf = { marks[it] },
                onOpen = onOpen,
            )
        }
    }
}

package com.moviepicker.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.ScreenHeader
import com.moviepicker.ui.design.TicketToggle
import com.moviepicker.ui.design.pressClickable
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

@Composable
fun LibraryScreen(onOpen: (Title) -> Unit, onSettings: () -> Unit) {
    val container = LocalContext.current.container
    val entities by container.repository.observeAll().collectAsStateWithLifecycle(emptyList())
    val profile by container.tasteProfile.collectAsStateWithLifecycle()
    val marks by container.marks.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Мои списки", kicker = "Pickture · мои билеты") {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, Brand.Perforation, RoundedCornerShape(6.dp))
                    .pressClickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Settings, "Настройки", tint = Brand.Text, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        TicketToggle(
            options = LibraryTab.entries.map { t ->
                val count = entities.count(t.filter)
                if (count > 0) "${t.label} · $count" else t.label
            },
            selected = tabIndex,
            onSelect = { tabIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AnimatedContent(
            targetState = tabIndex,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it * dir / 4 } + fadeOut(tween(160)))
            },
            label = "libraryTab",
        ) { index ->
            val shown = remember(entities, index) {
                LibraryTab.entries[index].let { t -> entities.filter(t.filter).map { it.toTitle() } }
            }
            if (shown.isEmpty()) {
                MessageBox("Пока пусто", LibraryTab.entries[index].empty)
            } else {
                TitleGrid(
                    items = shown,
                    matchOf = profile::matchPercent,
                    markOf = { marks[it] },
                    onOpen = onOpen,
                )
            }
        }
    }
}

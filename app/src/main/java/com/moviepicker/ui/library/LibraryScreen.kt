package com.moviepicker.ui.library

import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.moviepicker.ui.design.SectionHeader
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
import androidx.compose.material3.Icon
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

private enum class LibraryTab(
    val label: String,
    val icon: ImageVector,
    val empty: String,
    val filter: (UserTitleEntity) -> Boolean,
) {
    WATCHLIST("Хочу посмотреть", Icons.Filled.Bookmark, "Добавляйте сюда фильмы, и мы сообщим, когда они выйдут", { it.inWatchlist }),
    DIARY(
        "Нравится", Icons.Filled.Favorite,
        "Ставьте «Нравится» в карточке фильма и при желании уточняйте оценку — так рекомендации станут точнее",
        { it.inDiary },
    ),
    DISLIKED(
        "Не моё", Icons.Filled.ThumbDown, "Здесь будет то, что вам не интересно",
        { it.reaction < 0 && !it.watched && it.userRating == 0 },
    ),
}

@Composable
fun LibraryScreen(onOpen: (Title) -> Unit, onSettings: () -> Unit) {
    val container = LocalContext.current.container
    val entities by container.repository.observeAll().collectAsStateWithLifecycle(emptyList())
    val profile by container.tasteProfile.collectAsStateWithLifecycle()
    val marks by container.marks.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Мои списки") {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, Brand.Perforation, RoundedCornerShape(6.dp))
                    .pressClickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Settings, "Настройки", tint = Brand.Text, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        // Во вкладках только значок и число — полное название списка стоит заголовком ниже
        val counts = LibraryTab.entries.map { t -> entities.count(t.filter) }
        TicketToggle(
            options = counts.map { if (it > 0) "$it" else "" },
            icons = LibraryTab.entries.map { it.icon },
            descriptions = LibraryTab.entries.mapIndexed { i, t -> if (counts[i] > 0) "${t.label}: ${counts[i]}" else t.label },
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
            val tab = LibraryTab.entries[index]
            val shown = remember(entities, index) { entities.filter(tab.filter).map { it.toTitle() } }
            if (tab == LibraryTab.DIARY && shown.isNotEmpty()) {
                DiaryList(entities.filter(tab.filter), onOpen)
            } else if (shown.isEmpty()) {
                Column {
                    SectionHeader(tab.label, Modifier.padding(top = 12.dp))
                    MessageBox("Пока пусто", tab.empty)
                }
            } else {
                TitleGrid(
                    items = shown,
                    matchOf = profile::matchPercent,
                    markOf = { marks[it] },
                    onOpen = onOpen,
                    header = { SectionHeader(tab.label, inset = 0.dp) },
                )
            }
        }
    }
}

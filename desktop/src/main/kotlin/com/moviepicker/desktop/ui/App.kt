package com.moviepicker.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.moviepicker.data.FeedSection
import com.moviepicker.desktop.BuildInfo
import com.moviepicker.desktop.DesktopContainer
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.update.UpdateInfo

/** Экраны приложения. Вкладки слева — корневые, остальные открываются поверх с кнопкой «Назад». */
sealed interface Screen {
    data object Home : Screen
    data object Recs : Screen
    data object Search : Screen
    data object Library : Screen
    data object Settings : Screen
    data object ApiKey : Screen
    data object Genres : Screen
    data class Section(val section: FeedSection) : Screen
    data class Details(val key: TitleKey) : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun open(screen: Screen) = stack.add(screen)
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun root(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
    fun openTitle(title: Title) = open(Screen.Details(title.key))
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Новинки", Icons.Filled.NewReleases),
    Tab(Screen.Recs, "Для вас", Icons.Filled.AutoAwesome),
    Tab(Screen.Search, "Поиск", Icons.Filled.Search),
    Tab(Screen.Library, "Мои", Icons.Filled.CollectionsBookmark),
    Tab(Screen.Settings, "Настройки", Icons.Filled.Settings),
)

@Composable
fun PicktureApp(c: DesktopContainer) {
    MaterialTheme(colorScheme = PicktureColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val hasKey by c.hasApiKey.collectAsState()
            val settings by c.settings.settings.collectAsState(initial = null)
            val s = settings
            when {
                hasKey == null || s == null -> Unit
                hasKey == false -> ApiKeyScreen(c, onSaved = {}, onBack = null)
                !s.onboardingDone -> GenresScreen(c, firstRun = true, onDone = {})
                else -> MainLayout(c)
            }
        }
    }
}

@Composable
private fun MainLayout(c: DesktopContainer) {
    val nav = remember { Navigator() }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        update = runCatching { c.updates.check(BuildInfo.VERSION) }.getOrNull()
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxHeight(),
            header = {
                Text(
                    "Pickture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            },
        ) {
            val root = nav.stack.first()
            tabs.forEach { tab ->
                NavigationRailItem(
                    selected = root == tab.screen,
                    onClick = { nav.root(tab.screen) },
                    icon = { Icon(tab.icon, null) },
                    label = { Text(tab.label) },
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
            update?.let { info ->
                UpdateBanner(info, onClose = { update = null })
            }
            Box(Modifier.fillMaxSize()) {
                when (val screen = nav.current) {
                    Screen.Home -> HomeScreen(c, nav)
                    Screen.Recs -> RecsScreen(c, nav)
                    Screen.Search -> SearchScreen(c, nav)
                    Screen.Library -> LibraryScreen(c, nav)
                    Screen.Settings -> SettingsScreen(c, nav)
                    Screen.ApiKey -> ApiKeyScreen(c, onSaved = nav::back, onBack = nav::back)
                    Screen.Genres -> GenresScreen(c, firstRun = false, onDone = nav::back)
                    is Screen.Section -> SectionScreen(c, nav, screen.section)
                    is Screen.Details -> DetailsScreen(c, nav, screen.key)
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(info: UpdateInfo, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Доступна новая версия Pickture ${info.version} (у вас ${BuildInfo.VERSION})",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { openUrl(info.desktopZip?.url ?: info.pageUrl) }) { Text("Скачать") }
            TextButton(onClick = { openUrl(info.pageUrl) }) { Text("Что нового") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onClose) { Text("Позже") }
        }
    }
}

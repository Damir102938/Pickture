package com.moviepicker.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.moviepicker.desktop.ui.design.Brand
import com.moviepicker.desktop.ui.design.NavEntry
import com.moviepicker.desktop.ui.design.PkType
import com.moviepicker.desktop.ui.design.TextAction
import com.moviepicker.desktop.ui.design.TicketRail
import com.moviepicker.desktop.ui.design.TicketShape
import com.moviepicker.desktop.ui.design.picktureTypography
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

private val navEntries = tabs.map { NavEntry(it.label, it.icon) }

@Composable
fun PicktureApp(c: DesktopContainer) {
    MaterialTheme(colorScheme = PicktureColors, typography = picktureTypography(), shapes = PicktureShapes) {
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

/** Ключ для анимации переходов: экран и глубина стека (чтобы отличать «вперёд» от «назад»). */
private data class Frame(val screen: Screen, val depth: Int)

@Composable
private fun MainLayout(c: DesktopContainer) {
    val nav = remember { Navigator() }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        update = runCatching { c.updates.check(BuildInfo.VERSION) }.getOrNull()
    }

    Row(Modifier.fillMaxSize()) {
        val root = nav.stack.first()
        TicketRail(
            items = navEntries,
            selected = tabs.indexOfFirst { it.screen == root },
            onSelect = { nav.root(tabs[it].screen) },
        )
        Column(Modifier.fillMaxSize()) {
            update?.let { info ->
                UpdateBanner(info, onClose = { update = null })
            }
            AnimatedContent(
                targetState = Frame(nav.current, nav.stack.size),
                transitionSpec = { frameTransition(initialState, targetState) },
                contentKey = { it.screen to it.depth },
                modifier = Modifier.fillMaxSize(),
                label = "screens",
            ) { frame ->
                Box(Modifier.fillMaxSize()) {
                    when (val screen = frame.screen) {
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
}

/**
 * Вкладки «перематываются» вверх-вниз, как плёнка, — в ту сторону, где стоит вкладка в боковой ленте;
 * вложенные экраны выезжают снизу, как билет из кассы, и уезжают вниз при возврате.
 */
private fun frameTransition(from: Frame, to: Frame): ContentTransform {
    val easing = FastOutSlowInEasing
    val fromTab = tabs.indexOfFirst { it.screen == from.screen }
    val toTab = tabs.indexOfFirst { it.screen == to.screen }
    return when {
        from.depth == 1 && to.depth == 1 && fromTab >= 0 && toTab >= 0 -> {
            val dir = if (toTab > fromTab) 1 else -1
            (slideInVertically(tween(360, easing = easing)) { it * dir / 5 } + fadeIn(tween(280, delayMillis = 40)) +
                scaleIn(tween(360, easing = easing), initialScale = 0.96f)) togetherWith
                (slideOutVertically(tween(360, easing = easing)) { -it * dir / 5 } + fadeOut(tween(200)) +
                    scaleOut(tween(360, easing = easing), targetScale = 0.96f))
        }
        to.depth > from.depth ->
            (slideInVertically(tween(320, easing = easing)) { it / 10 } + fadeIn(tween(260))) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(300, easing = easing), targetScale = 0.97f))
        else ->
            (fadeIn(tween(260)) + scaleIn(tween(300, easing = easing), initialScale = 0.97f)) togetherWith
                (slideOutVertically(tween(280, easing = easing)) { it / 10 } + fadeOut(tween(220)))
    }
}

@Composable
private fun UpdateBanner(info: UpdateInfo, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .clip(TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp))
            .background(Brand.Ticket)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("НОВАЯ ВЕРСИЯ", style = PkType.label, color = Brand.Gold)
        Spacer(Modifier.width(12.dp))
        Text(
            "Доступна Pickture ${info.version} (у вас ${BuildInfo.VERSION})",
            color = Brand.Text,
            modifier = Modifier.weight(1f),
        )
        TextAction("Скачать", { openUrl(info.desktopZip?.url ?: info.pageUrl) }, color = Brand.Gold)
        TextAction("Что нового", { openUrl(info.pageUrl) })
        Spacer(Modifier.width(4.dp))
        TextAction("Позже", onClose, color = Brand.TextMuted)
    }
}

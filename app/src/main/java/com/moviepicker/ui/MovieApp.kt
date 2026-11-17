package com.moviepicker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moviepicker.container
import com.moviepicker.data.FeedSection
import com.moviepicker.data.local.Settings
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.ui.apikey.ApiKeyScreen
import com.moviepicker.ui.details.DetailsScreen
import com.moviepicker.ui.update.UpdatePrompt
import com.moviepicker.ui.home.HomeScreen
import com.moviepicker.ui.library.LibraryScreen
import com.moviepicker.ui.onboarding.OnboardingScreen
import com.moviepicker.ui.recs.RecommendationsScreen
import com.moviepicker.ui.search.SearchScreen
import com.moviepicker.ui.section.SectionScreen
import com.moviepicker.ui.settings.SettingsScreen
import com.moviepicker.ui.trailer.TrailerScreen
import com.moviepicker.work.Notifications

private object Routes {
    const val HOME = "home"
    const val RECS = "recs"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val API_KEY = "apikey"
    const val ONBOARDING = "onboarding"
    const val ONBOARDING_EDIT = "onboarding/edit"
    const val SECTION = "section/{name}"
    const val DETAILS = "details/{type}/{id}"
    const val TRAILER = "trailer/{type}/{id}/{video}"

    fun section(s: FeedSection) = "section/${s.name}"
    fun details(key: TitleKey) = "details/${key.type.path}/${key.id}"
    fun trailer(key: TitleKey, video: String) = "trailer/${key.type.path}/${key.id}/$video"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    BottomTab(Routes.HOME, "Новинки", Icons.Filled.NewReleases),
    BottomTab(Routes.RECS, "Для вас", Icons.Filled.AutoAwesome),
    BottomTab(Routes.SEARCH, "Поиск", Icons.Filled.Search),
    BottomTab(Routes.LIBRARY, "Мои", Icons.Filled.CollectionsBookmark),
)

@Composable
fun MovieApp(startWithOnboarding: Boolean, deepLink: TitleKey?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = tabs.any { it.route == route }

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            nav.navigate(Routes.details(deepLink))
            onDeepLinkHandled()
        }
    }
    AskNotificationPermissionOnce(active = route == Routes.HOME)
    UpdatePrompt()

    val openTitle: (Title) -> Unit = { nav.navigate(Routes.details(it.key)) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startWithOnboarding) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(edit = false, onDone = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.ONBOARDING_EDIT) {
                OnboardingScreen(edit = true, onDone = { nav.popBackStack() })
            }
            composable(Routes.HOME) {
                HomeScreen(onOpen = openTitle, onSection = { nav.navigate(Routes.section(it)) })
            }
            composable(Routes.RECS) {
                RecommendationsScreen(onOpen = openTitle, onSetupTaste = { nav.navigate(Routes.ONBOARDING_EDIT) })
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpen = openTitle)
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onOpen = openTitle, onSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.API_KEY) {
                ApiKeyScreen(onSaved = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onEditOnboarding = { nav.navigate(Routes.ONBOARDING_EDIT) },
                    onEditApiKey = { nav.navigate(Routes.API_KEY) },
                )
            }
            composable(
                Routes.SECTION,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val section = FeedSection.valueOf(entry.arguments?.getString("name") ?: FeedSection.TRENDING.name)
                SectionScreen(section, onBack = { nav.popBackStack() }, onOpen = openTitle)
            }
            composable(
                Routes.DETAILS,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                ),
            ) { entry ->
                val type = MediaType.fromPath(entry.arguments?.getString("type")) ?: MediaType.MOVIE
                val id = entry.arguments?.getInt("id") ?: 0
                val key = TitleKey(type, id)
                DetailsScreen(
                    key,
                    onBack = { nav.popBackStack() },
                    onOpen = openTitle,
                    onTrailer = { video -> nav.navigate(Routes.trailer(key, video)) },
                )
            }
            composable(
                Routes.TRAILER,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                    navArgument("video") { type = NavType.StringType },
                ),
            ) { entry ->
                val type = MediaType.fromPath(entry.arguments?.getString("type")) ?: MediaType.MOVIE
                val id = entry.arguments?.getInt("id") ?: 0
                val video = entry.arguments?.getString("video").orEmpty()
                TrailerScreen(TitleKey(type, id), video, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** На Android 13+ один раз спрашиваем разрешение на уведомления, когда пользователь дошёл до ленты. */
@Composable
private fun AskNotificationPermissionOnce(active: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null as Settings?)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val s = settings
    LaunchedEffect(active, s?.notificationPermissionAsked) {
        if (active && s != null && s.notificationsEnabled && !s.notificationPermissionAsked &&
            !Notifications.canPost(context)
        ) {
            container.settings.setNotificationPermissionAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

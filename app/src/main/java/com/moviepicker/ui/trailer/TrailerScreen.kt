package com.moviepicker.ui.trailer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.PicktureTopBar
import com.moviepicker.ui.design.SectionHeader
import com.moviepicker.ui.design.TicketOutlineButton
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.pressClickable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.moviepicker.AppContainer
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.Video
import com.moviepicker.ui.common.appViewModel
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrailerViewModel(
    private val container: AppContainer,
    private val titleKey: TitleKey,
    initialVideo: String,
) : ViewModel() {
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos = _videos.asStateFlow()
    private val _current = MutableStateFlow(initialVideo)
    val current = _current.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            // Список роликов не обязателен: текущий трейлер играет и без него
            runCatching { container.repository.videos(titleKey) }.onSuccess { _videos.value = it }
        }
    }

    fun select(key: String) {
        _error.value = null
        _current.value = key
    }

    fun onPlayerError(error: PlayerConstants.PlayerError) {
        _error.value = when (error) {
            PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                "Автор ролика запретил показывать его в других приложениях."
            PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> "Ролик удалён или скрыт."
            else -> "Не удалось воспроизвести. Возможно, YouTube недоступен без VPN."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailerScreen(titleKey: TitleKey, videoKey: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "trailer:${titleKey.type.path}:${titleKey.id}") {
        TrailerViewModel(it, titleKey, videoKey)
    }
    val videos by vm.videos.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val uriHandler = LocalUriHandler.current
    val currentVideo = videos.firstOrNull { it.key == current }

    FullscreenSystemBars(enabled = landscape)

    // Плеер стоит в одном и том же месте дерева в обеих ориентациях,
    // поэтому при повороте видео продолжается, а не начинается заново.
    Column(Modifier.fillMaxSize().background(if (landscape) Color.Black else MaterialTheme.colorScheme.background)) {
        if (!landscape) {
            PicktureTopBar(currentVideo?.name ?: "Трейлер", onBack = onBack, modifier = Modifier.statusBarsPadding())
        }
        Box(
            if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            YouTubeVideoPlayer(videoKey = current, onError = vm::onPlayerError, modifier = Modifier.fillMaxSize())
        }
        if (!landscape) {
            if (error != null) {
                Surface(
                    color = Brand.Ticket,
                    shape = TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(error!!, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        TicketOutlineButton("Открыть на YouTube", { uriHandler.openUri("https://www.youtube.com/watch?v=$current") })
                    }
                }
            }
            Text(
                "Поверните телефон, чтобы смотреть на весь экран",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (videos.size > 1) {
                SectionHeader("Все ролики", number = videos.size)
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    items(videos, key = { it.key }) { video ->
                        VideoRow(video, selected = video.key == current, onClick = { vm.select(video.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: Video, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.border(1.5.dp, Brand.Gold, RoundedCornerShape(8.dp)) else Modifier)
            .background(if (selected) Brand.Ticket else Color.Transparent)
            .pressClickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = "https://i.ytimg.com/vi/${video.key}/mqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                video.typeLabel + (video.language?.let { " · ${it.uppercase()}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Встроенный плеер YouTube (официальный IFrame-плеер в WebView, без приложения YouTube). */
@Composable
private fun YouTubeVideoPlayer(
    videoKey: String,
    onError: (PlayerConstants.PlayerError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    val latestKey by rememberUpdatedState(videoKey)
    val latestOnError by rememberUpdatedState(onError)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                // Пауза при сворачивании, освобождение при уходе с экрана
                lifecycle.addObserver(this)
                val options = IFramePlayerOptions.Builder(context)
                    .controls(1)
                    .rel(0)
                    .ivLoadPolicy(3)
                    .ccLoadPolicy(0)
                    .fullscreen(0)
                    .build()
                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            player = youTubePlayer
                            youTubePlayer.loadVideo(latestKey, 0f)
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            latestOnError(error)
                        }
                    },
                    true,
                    options,
                )
            }
        },
        onRelease = { view ->
            lifecycle.removeObserver(view)
            view.release()
        },
    )

    // Смена ролика из списка
    LaunchedEffect(videoKey) {
        player?.loadVideo(videoKey, 0f)
    }
}

/** В горизонтальном положении прячем системные панели — видео на весь экран. */
@Composable
private fun FullscreenSystemBars(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

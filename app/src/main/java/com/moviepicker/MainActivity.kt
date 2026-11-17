package com.moviepicker

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviepicker.data.local.Settings
import com.moviepicker.data.remote.TmdbApi
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.TitleKey
import com.moviepicker.ui.MovieApp
import com.moviepicker.ui.common.MessageBox
import com.moviepicker.ui.theme.MoviePickerTheme
import com.moviepicker.work.Notifications

class MainActivity : ComponentActivity() {

    /** Тайтл, который нужно открыть после нажатия на уведомление. */
    private val deepLink = mutableStateOf<TitleKey?>(null)

    /** Настройки прочитаны — можно убирать сплэш. */
    @Volatile
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val startedAt = SystemClock.uptimeMillis()
        // Держим сплэш, пока не прочитаны настройки и не доиграла анимация знака
        splash.setKeepOnScreenCondition {
            !contentReady || SystemClock.uptimeMillis() - startedAt < SPLASH_MIN_MS
        }
        splash.setOnExitAnimationListener { provider ->
            // Значка может не быть (например, при запуске из уведомления) — тогда просто растворяем фон
            val icon = runCatching { provider.iconView }.getOrNull()
            icon?.animate()
                ?.scaleX(1.35f)?.scaleY(1.35f)
                ?.alpha(0f)
                ?.setDuration(SPLASH_EXIT_MS)
                ?.setInterpolator(AccelerateInterpolator())
                ?.start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(SPLASH_EXIT_MS / 3)
                .setDuration(SPLASH_EXIT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            MoviePickerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val settings by container.settings.settings
                        .collectAsStateWithLifecycle(initialValue = null as Settings?)
                    val current = settings
                    if (current != null || !TmdbApi.hasApiKey) contentReady = true
                    when {
                        !TmdbApi.hasApiKey -> MessageBox(
                            title = "Нужен ключ TMDB API",
                            text = "1. Зарегистрируйтесь на themoviedb.org\n" +
                                "2. Settings → API → создайте ключ (бесплатно)\n" +
                                "3. Добавьте в файл local.properties в корне проекта строку\n" +
                                "TMDB_API_KEY=ваш_ключ\n" +
                                "4. Пересоберите приложение",
                            modifier = Modifier.safeDrawingPadding(),
                        )
                        current == null -> Unit // настройки ещё читаются с диска — доли секунды
                        else -> {
                            val startWithOnboarding = remember { !current.onboardingDone }
                            MovieApp(
                                startWithOnboarding = startWithOnboarding,
                                deepLink = deepLink.value,
                                onDeepLinkHandled = { deepLink.value = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private companion object {
        const val SPLASH_MIN_MS = 1200L
        const val SPLASH_EXIT_MS = 320L
    }

    private fun handleIntent(intent: Intent?) {
        val type = MediaType.fromPath(intent?.getStringExtra(Notifications.EXTRA_TYPE)) ?: return
        val id = intent?.getIntExtra(Notifications.EXTRA_ID, 0) ?: 0
        if (id > 0) deepLink.value = TitleKey(type, id)
    }
}

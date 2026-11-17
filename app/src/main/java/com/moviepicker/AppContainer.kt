package com.moviepicker

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.moviepicker.data.BackupManager
import com.moviepicker.data.BackupService
import com.moviepicker.data.TitleRepository
import com.moviepicker.data.local.AppDatabase
import com.moviepicker.data.local.SettingsStore
import com.moviepicker.data.remote.ApiKeyHolder
import com.moviepicker.data.remote.TmdbApi
import com.moviepicker.data.remote.TmdbKeyValidator
import com.moviepicker.domain.Images
import com.moviepicker.domain.RecommendationEngine
import com.moviepicker.domain.TasteProfile
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.update.AppUpdater
import com.moviepicker.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Простой ручной DI: все синглтоны приложения. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(context.settingsDataStore)
    private val database = AppDatabase.create(context)

    /** Ключ TMDB: введённый пользователем, а в debug-сборке — запасной из local.properties. */
    val apiKey = ApiKeyHolder()
    private fun effectiveKey(userKey: String) = userKey.ifBlank { BuildConfig.TMDB_API_KEY }

    private val api = TmdbApi.create(
        baseUrl = BuildConfig.TMDB_API_URL,
        keyProvider = {
            // Первый запрос может прийти раньше, чем настройки прочитаны, — дочитываем синхронно
            // (это фоновый поток OkHttp, не главный)
            apiKey.key.ifBlank {
                runBlocking { effectiveKey(settings.current().tmdbApiKey) }.also { apiKey.key = it }
            }
        },
        logger = if (BuildConfig.DEBUG) { message -> Log.d("TMDB", message) } else null,
    )
    val keyValidator = TmdbKeyValidator(BuildConfig.TMDB_API_URL)

    val repository = TitleRepository(api, database.userTitles(), settings, appScope)
    val recommendations = RecommendationEngine(repository, settings)
    val backup = BackupManager(context, BackupService(repository, settings, recommendations))
    val updater = AppUpdater(context, UpdateChecker())

    /** Есть ли ключ TMDB (без него приложение показывает экран ввода ключа). */
    val hasApiKey: StateFlow<Boolean?> = settings.settings
        .map { effectiveKey(it.tmdbApiKey).isNotBlank() }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    /** Текущий вкус пользователя — для процентов совпадения на карточках. */
    val tasteProfile: StateFlow<TasteProfile> =
        combine(repository.observeAll(), settings.settings) { entities, s -> TasteProfile.build(entities, s) }
            .stateIn(appScope, SharingStarted.Eagerly, TasteProfile.EMPTY)

    /** Отметки пользователя (лайк/дизлайк/«хочу посмотреть») для иконок на карточках. */
    val marks: StateFlow<Map<TitleKey, UserMark>> =
        repository.observeMarks().stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    init {
        Images.baseUrl = BuildConfig.TMDB_IMAGE_URL
        appScope.launch {
            settings.settings.collect { apiKey.key = effectiveKey(it.tmdbApiKey) }
        }
    }
}

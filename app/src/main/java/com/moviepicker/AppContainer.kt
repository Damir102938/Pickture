package com.moviepicker

import android.content.Context
import com.moviepicker.data.BackupManager
import com.moviepicker.data.TitleRepository
import com.moviepicker.data.local.AppDatabase
import com.moviepicker.data.local.SettingsStore
import com.moviepicker.data.remote.TmdbApi
import com.moviepicker.domain.RecommendationEngine
import com.moviepicker.domain.TasteProfile
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Простой ручной DI: все синглтоны приложения. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(context)
    private val database = AppDatabase.create(context)
    private val api = TmdbApi.create(debug = BuildConfig.DEBUG)
    val repository = TitleRepository(api, database.userTitles(), settings, appScope)
    val recommendations = RecommendationEngine(repository, settings)
    val backup = BackupManager(context, repository, settings, recommendations)

    /** Текущий вкус пользователя — для процентов совпадения на карточках. */
    val tasteProfile: StateFlow<TasteProfile> =
        combine(repository.observeAll(), settings.settings) { entities, s -> TasteProfile.build(entities, s) }
            .stateIn(appScope, SharingStarted.Eagerly, TasteProfile.EMPTY)

    /** Отметки пользователя (лайк/дизлайк/«хочу посмотреть») для иконок на карточках. */
    val marks: StateFlow<Map<TitleKey, UserMark>> =
        repository.observeMarks().stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

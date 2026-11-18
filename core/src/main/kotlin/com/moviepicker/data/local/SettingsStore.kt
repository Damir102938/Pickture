package com.moviepicker.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val onboardingDone: Boolean = false,
    /** Учитывать ли жанры и фильмы со стартового экрана в рекомендациях. */
    val useOnboarding: Boolean = true,
    val favoriteGenres: Set<Int> = emptySet(),
    val notificationsEnabled: Boolean = true,
    /** Сообщать о каждой новой серии сериалов из «Хочу посмотреть» и просмотренных с хорошей оценкой. */
    val notifyEpisodes: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
    /** Ключ TMDB, введённый пользователем. */
    val tmdbApiKey: String = "",
    /** Показать подсказку «оцените отмеченные фильмы» — один раз после первого стартового экрана. */
    val ratingHintPending: Boolean = false,
)

/** Настройки приложения поверх DataStore (на Android — файл приложения, на ПК — файл в профиле). */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val useOnboarding = booleanPreferencesKey("use_onboarding")
        val favoriteGenres = stringSetPreferencesKey("favorite_genres")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val notifyEpisodes = booleanPreferencesKey("notify_episodes")
        val permissionAsked = booleanPreferencesKey("notification_permission_asked")
        val notifiedNew = stringPreferencesKey("notified_new_titles")
        val tmdbApiKey = stringPreferencesKey("tmdb_api_key")
        val ratingHintPending = booleanPreferencesKey("rating_hint_pending")
    }

    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[Keys.onboardingDone] ?: false,
        useOnboarding = this[Keys.useOnboarding] ?: true,
        favoriteGenres = this[Keys.favoriteGenres].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        notificationsEnabled = this[Keys.notificationsEnabled] ?: true,
        notifyEpisodes = this[Keys.notifyEpisodes] ?: true,
        notificationPermissionAsked = this[Keys.permissionAsked] ?: false,
        tmdbApiKey = this[Keys.tmdbApiKey].orEmpty(),
        ratingHintPending = this[Keys.ratingHintPending] ?: false,
    )

    suspend fun completeOnboarding(genres: Set<Int>) = dataStore.edit {
        it[Keys.onboardingDone] = true
        it[Keys.favoriteGenres] = genres.map(Int::toString).toSet()
    }

    suspend fun setFavoriteGenres(genres: Set<Int>) = dataStore.edit {
        it[Keys.favoriteGenres] = genres.map(Int::toString).toSet()
    }

    suspend fun setUseOnboarding(value: Boolean) = dataStore.edit { it[Keys.useOnboarding] = value }

    suspend fun setNotificationsEnabled(value: Boolean) =
        dataStore.edit { it[Keys.notificationsEnabled] = value }

    suspend fun setNotifyEpisodes(value: Boolean) = dataStore.edit { it[Keys.notifyEpisodes] = value }

    suspend fun setNotificationPermissionAsked() =
        dataStore.edit { it[Keys.permissionAsked] = true }

    suspend fun setRatingHintPending(value: Boolean) = dataStore.edit { it[Keys.ratingHintPending] = value }

    suspend fun setTmdbApiKey(key: String) = dataStore.edit { it[Keys.tmdbApiKey] = key.trim() }

    /** Ключи новинок, о которых уже присылали уведомление (хранится последние 300). */
    suspend fun notifiedNewTitles(): List<String> =
        dataStore.data.first()[Keys.notifiedNew].orEmpty().split('|').filter { it.isNotBlank() }

    suspend fun addNotifiedNewTitles(keys: List<String>) = dataStore.edit {
        val existing = it[Keys.notifiedNew].orEmpty().split('|').filter { k -> k.isNotBlank() }
        it[Keys.notifiedNew] = (existing + keys).distinct().takeLast(300).joinToString("|")
    }
}

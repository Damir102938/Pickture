package com.moviepicker.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val onboardingDone: Boolean = false,
    /** Учитывать ли жанры и фильмы со стартового экрана в рекомендациях. */
    val useOnboarding: Boolean = true,
    val favoriteGenres: Set<Int> = emptySet(),
    val notificationsEnabled: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val useOnboarding = booleanPreferencesKey("use_onboarding")
        val favoriteGenres = stringSetPreferencesKey("favorite_genres")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val permissionAsked = booleanPreferencesKey("notification_permission_asked")
        val notifiedNew = stringPreferencesKey("notified_new_titles")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[Keys.onboardingDone] ?: false,
        useOnboarding = this[Keys.useOnboarding] ?: true,
        favoriteGenres = this[Keys.favoriteGenres].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        notificationsEnabled = this[Keys.notificationsEnabled] ?: true,
        notificationPermissionAsked = this[Keys.permissionAsked] ?: false,
    )

    suspend fun completeOnboarding(genres: Set<Int>) = context.dataStore.edit {
        it[Keys.onboardingDone] = true
        it[Keys.favoriteGenres] = genres.map(Int::toString).toSet()
    }

    suspend fun setUseOnboarding(value: Boolean) = context.dataStore.edit { it[Keys.useOnboarding] = value }

    suspend fun setNotificationsEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.notificationsEnabled] = value }

    suspend fun setNotificationPermissionAsked() =
        context.dataStore.edit { it[Keys.permissionAsked] = true }

    /** Ключи новинок, о которых уже присылали уведомление (хранится последние 300). */
    suspend fun notifiedNewTitles(): List<String> =
        context.dataStore.data.first()[Keys.notifiedNew].orEmpty().split('|').filter { it.isNotBlank() }

    suspend fun addNotifiedNewTitles(keys: List<String>) = context.dataStore.edit {
        val existing = it[Keys.notifiedNew].orEmpty().split('|').filter { k -> k.isNotBlank() }
        it[Keys.notifiedNew] = (existing + keys).distinct().takeLast(300).joinToString("|")
    }
}

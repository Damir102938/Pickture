package com.moviepicker.data

import android.content.Context
import android.net.Uri
import com.moviepicker.data.local.SettingsStore
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.domain.RecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime

/** Формат файла резервной копии. Новые поля добавлять только со значениями по умолчанию. */
@Serializable
data class Backup(
    val app: String = APP_ID,
    val version: Int = 1,
    @SerialName("exported_at") val exportedAt: String = "",
    @SerialName("favorite_genres") val favoriteGenres: List<Int> = emptyList(),
    @SerialName("use_onboarding") val useOnboarding: Boolean = true,
    val titles: List<UserTitleEntity> = emptyList(),
) {
    companion object {
        const val APP_ID = "Pickture"
    }
}

data class ImportResult(val total: Int, val applied: Int)

class BackupException(message: String) : Exception(message)

/** Сохранение оценок и списков в JSON-файл и восстановление из него. */
class BackupManager(
    context: Context,
    private val repository: TitleRepository,
    private val settings: SettingsStore,
    private val recommendations: RecommendationEngine,
) {
    private val resolver = context.applicationContext.contentResolver
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun suggestedFileName(): String = "pickture-backup-${LocalDate.now()}.json"

    /** Возвращает количество сохранённых тайтлов. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val current = settings.current()
        val backup = Backup(
            exportedAt = OffsetDateTime.now().toString(),
            favoriteGenres = current.favoriteGenres.sorted(),
            useOnboarding = current.useOnboarding,
            titles = repository.allMarked(),
        )
        val stream = resolver.openOutputStream(uri, "wt") ?: throw BackupException("Не удалось открыть файл для записи")
        stream.use { it.write(json.encodeToString(Backup.serializer(), backup).toByteArray()) }
        backup.titles.size
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw BackupException("Не удалось открыть файл")
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("Это не файл резервной копии Pickture")
        } catch (e: IllegalArgumentException) {
            throw BackupException("Это не файл резервной копии Pickture")
        }
        if (backup.app != Backup.APP_ID) throw BackupException("Это не файл резервной копии Pickture")

        val applied = repository.importEntities(backup.titles)
        val current = settings.current()
        settings.setUseOnboarding(backup.useOnboarding)
        // Жанры объединяем, онбординг считаем пройденным
        settings.completeOnboarding(current.favoriteGenres + backup.favoriteGenres)
        recommendations.invalidate()
        ImportResult(total = backup.titles.size, applied = applied)
    }
}

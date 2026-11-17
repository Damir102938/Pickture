package com.moviepicker.data

import com.moviepicker.data.local.SettingsStore
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.domain.RecommendationEngine
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

/**
 * Резервная копия оценок и списков в JSON. Файлы одинаковые на Android и ПК,
 * так что можно перенести оценки с телефона на компьютер и обратно.
 */
class BackupService(
    private val repository: TitleRepository,
    private val settings: SettingsStore,
    private val recommendations: RecommendationEngine,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun suggestedFileName(): String = "pickture-backup-${LocalDate.now()}.json"

    /** Возвращает JSON и количество сохранённых тайтлов. */
    suspend fun exportJson(): Pair<String, Int> {
        val current = settings.current()
        val backup = Backup(
            exportedAt = OffsetDateTime.now().toString(),
            favoriteGenres = current.favoriteGenres.sorted(),
            useOnboarding = current.useOnboarding,
            titles = repository.allMarked(),
        )
        return json.encodeToString(Backup.serializer(), backup) to backup.titles.size
    }

    suspend fun importJson(text: String): ImportResult {
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
        return ImportResult(total = backup.titles.size, applied = applied)
    }
}

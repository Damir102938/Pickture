package com.moviepicker.data.local

import androidx.room.Entity
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.parseDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Всё, что пользователь отметил: лайк/дизлайк, «хочу посмотреть».
 * Хранится снимок карточки (чтобы списки работали без сети) и признаки для рекомендаций.
 * Сериализуется целиком в файл резервной копии.
 */
@Serializable
@Entity(tableName = "user_titles", primaryKeys = ["type", "id"])
data class UserTitleEntity(
    val id: Int,
    val type: String,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?,
    val date: String?,
    val rating: Double,
    val voteCount: Int,
    val genreIds: String,
    val reaction: Int = 0,
    val inWatchlist: Boolean = false,
    /** Отмечено на стартовом экране (можно отключить в настройках). */
    val fromOnboarding: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    // Признаки для рекомендаций, подгружаются из деталей
    val featuresLoaded: Boolean = false,
    val keywordIds: String = "",
    val castIds: String = "",
    val directorIds: String = "",
    // Состояние уведомлений: 0 — не было, 1 — сообщили о премьере, 2 — о цифровом релизе
    val releaseNotifyStage: Int = 0,
    /** Последний вышедший сезон сериала, о котором пользователь уже знает. */
    val lastKnownSeason: Int = 0,
    // Дневник просмотров (с версии 0.6)
    /** Пользователь отметил, что посмотрел. */
    val watched: Boolean = false,
    /** Оценка пользователя 1..10, 0 — без оценки. Для вкуса важнее лайка. */
    val userRating: Int = 0,
    /** День, когда поставлены лайк или оценка (ISO yyyy-MM-dd); ставится автоматически. */
    val watchedAt: String? = null,
    /** Последняя вышедшая серия, о которой пользователь знает: сезон * 1000 + номер серии. */
    val lastKnownEpisode: Int = 0,
) {
    val mediaType: MediaType get() = MediaType.fromPath(type) ?: MediaType.MOVIE
    val reactionValue: Reaction get() = Reaction.of(reaction)

    /** В дневнике — всё, что понравилось или оценено. */
    val inDiary: Boolean get() = reaction > 0 || watched || userRating > 0

    /** Дата записи в дневнике; у старых отметок без даты — день последнего изменения. */
    val diaryDate: LocalDate
        get() = parseDate(watchedAt)
            ?: Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Пустая запись — ни оценки, ни списка, ни просмотра: её можно удалить. */
    val isBlank: Boolean get() = reaction == 0 && !inWatchlist && !watched

    fun toTitle() = Title(
        id = id,
        type = mediaType,
        name = name,
        originalName = null,
        overview = "",
        posterPath = posterPath,
        backdropPath = backdropPath,
        date = parseDate(date),
        rating = rating,
        voteCount = voteCount,
        popularity = 0.0,
        genreIds = genreIds.toIntList(),
    )

    companion object {
        fun from(title: Title) = UserTitleEntity(
            id = title.id,
            type = title.type.path,
            name = title.name,
            posterPath = title.posterPath,
            backdropPath = title.backdropPath,
            date = title.date?.toString(),
            rating = title.rating,
            voteCount = title.voteCount,
            genreIds = title.genreIds.joinToString(","),
        )
    }
}

fun String.toIntList(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }

/**
 * Хранилище отметок пользователя. На Android — Room, на ПК — JSON-файл.
 */
interface UserTitleStore {
    suspend fun get(type: String, id: Int): UserTitleEntity?
    fun observe(type: String, id: Int): Flow<UserTitleEntity?>
    fun observeAll(): Flow<List<UserTitleEntity>>
    suspend fun getAll(): List<UserTitleEntity>
    suspend fun upsert(entity: UserTitleEntity)
    suspend fun delete(type: String, id: Int)
    suspend fun clear()
}

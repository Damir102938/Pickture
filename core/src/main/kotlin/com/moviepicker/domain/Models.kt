package com.moviepicker.domain

import java.time.LocalDate

enum class MediaType(val path: String, val label: String) {
    MOVIE("movie", "Фильм"),
    TV("tv", "Сериал");

    companion object {
        fun fromPath(path: String?): MediaType? = entries.firstOrNull { it.path == path }
    }
}

/** Краткая информация о фильме или сериале — то, что показывается в карточках. */
data class Title(
    val id: Int,
    val type: MediaType,
    val name: String,
    val originalName: String?,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val date: LocalDate?,
    val rating: Double,
    val voteCount: Int,
    val popularity: Double,
    val genreIds: List<Int>,
) {
    val key: TitleKey get() = TitleKey(type, id)
    val year: Int? get() = date?.year
}

data class TitleKey(val type: MediaType, val id: Int)

enum class Reaction(val value: Int) {
    NONE(0), LIKE(1), DISLIKE(-1);

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/** Отношение пользователя к тайтлу. */
data class UserMark(
    val reaction: Reaction = Reaction.NONE,
    val inWatchlist: Boolean = false,
    val watched: Boolean = false,
    /** Оценка 1..10, 0 — без оценки. */
    val rating: Int = 0,
    val watchedAt: LocalDate? = null,
)

/**
 * Оценка уточняет лайк или «Не моё»: после лайка можно выбрать 6–10, после «Не моё» — 1–5.
 * Вес для вкуса: 9–10 — сильный плюс, 7–8 — плюс, 6 и 5 — почти нейтрально, 1–4 — минус.
 * Лайк без уточнения весит как «9», «Не моё» — как «2».
 */
object RatingScale {
    fun weight(rating: Int): Double = when (rating) {
        10 -> 1.25
        9 -> 1.0
        8 -> 0.65
        7 -> 0.35
        6 -> 0.15
        5 -> -0.25
        4 -> -0.5
        3 -> -0.75
        2 -> -1.0
        1 -> -1.25
        else -> 0.0
    }

    /** Лайк или дизлайк, который ставится вместе с оценкой. */
    fun reactionFor(rating: Int): Reaction = when {
        rating >= 6 -> Reaction.LIKE
        rating in 1..5 -> Reaction.DISLIKE
        else -> Reaction.NONE
    }

    /** Какие оценки можно выбрать для уточнения реакции. */
    fun range(reaction: Reaction): IntRange = when (reaction) {
        Reaction.LIKE -> 6..10
        Reaction.DISLIKE -> 1..5
        Reaction.NONE -> IntRange.EMPTY
    }

    fun label(rating: Int): String = when (rating) {
        10 -> "Шедевр"
        9 -> "Отлично"
        8 -> "Очень хорошо"
        7 -> "Хорошо"
        6 -> "Неплохо"
        5 -> "Средне"
        4 -> "Слабо"
        3 -> "Плохо"
        2 -> "Очень плохо"
        1 -> "Ужасно"
        else -> "Без оценки"
    }
}

data class CastMember(val id: Int, val name: String, val character: String?, val photoPath: String?)

/** Ролик с YouTube: трейлер, тизер и т. п. */
data class Video(val key: String, val name: String, val type: String, val language: String?) {
    val typeLabel: String
        get() = when (type) {
            "Trailer" -> "Трейлер"
            "Teaser" -> "Тизер"
            "Clip" -> "Отрывок"
            "Featurette" -> "О съёмках"
            "Behind the Scenes" -> "За кадром"
            else -> "Видео"
        }
}

data class Episode(val season: Int, val number: Int, val date: LocalDate?, val name: String?) {
    /** Порядковый код для сравнения: сезон * 1000 + номер серии. */
    val code: Int get() = season * 1000 + number
}

data class TitleDetails(
    val title: Title,
    val genres: List<String>,
    val tagline: String?,
    val runtimeMinutes: Int?,
    val seasons: Int?,
    val episodes: Int?,
    val status: String?,
    val cast: List<CastMember>,
    val directors: List<String>,
    /** Трейлеры и тизеры, лучшие первыми (русские, официальные). */
    val videos: List<Video>,
    val theatricalDate: LocalDate?,
    val digitalDate: LocalDate?,
    val lastEpisode: Episode?,
    val nextEpisode: Episode?,
    val similar: List<Title>,
    // Признаки для рекомендаций
    val keywordIds: List<Int>,
    val castIds: List<Int>,
    val directorIds: List<Int>,
)

object Images {
    const val DEFAULT_BASE_URL = "https://image.tmdb.org/t/p/"

    /** Можно заменить на прокси при старте приложения. */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
        }

    private val base: String get() = baseUrl

    fun poster(path: String?, size: String = "w342"): String? = path?.let { "$base$size$it" }
    fun backdrop(path: String?, size: String = "w780"): String? = path?.let { "$base$size$it" }
    fun profile(path: String?, size: String = "w185"): String? = path?.let { "$base$size$it" }
}

fun parseDate(value: String?): LocalDate? =
    if (value.isNullOrBlank()) null else runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

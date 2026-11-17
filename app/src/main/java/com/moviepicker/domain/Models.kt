package com.moviepicker.domain

import com.moviepicker.BuildConfig
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
)

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

data class Episode(val season: Int, val number: Int, val date: LocalDate?, val name: String?)

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
    private val base = BuildConfig.TMDB_IMAGE_URL.let { if (it.endsWith("/")) it else "$it/" }

    fun poster(path: String?, size: String = "w342"): String? = path?.let { "$base$size$it" }
    fun backdrop(path: String?, size: String = "w780"): String? = path?.let { "$base$size$it" }
    fun profile(path: String?, size: String = "w185"): String? = path?.let { "$base$size$it" }
}

fun parseDate(value: String?): LocalDate? =
    if (value.isNullOrBlank()) null else runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

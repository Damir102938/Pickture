package com.moviepicker.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

/** Элемент списка: фильм, сериал или (в поиске) персона. */
@Serializable
data class TitleDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
)

@Serializable
data class GenreDto(val id: Int, val name: String = "")

@Serializable
data class CastDto(
    val id: Int,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 999,
)

@Serializable
data class CrewDto(
    val id: Int,
    val name: String = "",
    val job: String? = null,
)

@Serializable
data class CreditsDto(
    val cast: List<CastDto> = emptyList(),
    val crew: List<CrewDto> = emptyList(),
)

@Serializable
data class KeywordDto(val id: Int, val name: String = "")

/** У фильмов ключевые слова лежат в `keywords`, у сериалов в `results`. */
@Serializable
data class KeywordsDto(
    val keywords: List<KeywordDto> = emptyList(),
    val results: List<KeywordDto> = emptyList(),
) {
    val all: List<KeywordDto> get() = keywords + results
}

@Serializable
data class VideoDto(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val name: String = "",
    val official: Boolean = false,
    @SerialName("iso_639_1") val language: String? = null,
)

@Serializable
data class VideosDto(val results: List<VideoDto> = emptyList())

@Serializable
data class ReleaseDateDto(
    val type: Int = 0,
    @SerialName("release_date") val releaseDate: String = "",
)

@Serializable
data class CountryReleaseDatesDto(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releaseDates: List<ReleaseDateDto> = emptyList(),
)

@Serializable
data class ReleaseDatesDto(val results: List<CountryReleaseDatesDto> = emptyList())

@Serializable
data class EpisodeDto(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val name: String? = null,
)

@Serializable
data class CreatorDto(val id: Int, val name: String = "")

@Serializable
data class DetailsDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    val genres: List<GenreDto> = emptyList(),
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val status: String? = null,
    @SerialName("last_episode_to_air") val lastEpisodeToAir: EpisodeDto? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: EpisodeDto? = null,
    @SerialName("created_by") val createdBy: List<CreatorDto> = emptyList(),
    val credits: CreditsDto? = null,
    val keywords: KeywordsDto? = null,
    val videos: VideosDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    val recommendations: PagedResponse<TitleDto>? = null,
)

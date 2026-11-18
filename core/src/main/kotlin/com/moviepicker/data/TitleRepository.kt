package com.moviepicker.data

import com.moviepicker.data.local.SettingsStore
import com.moviepicker.data.local.UserTitleStore
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.data.remote.DetailsDto
import com.moviepicker.data.remote.EpisodeDto
import com.moviepicker.data.remote.TitleDto
import com.moviepicker.data.remote.TmdbApi
import com.moviepicker.data.remote.VideoDto
import com.moviepicker.domain.CastMember
import com.moviepicker.domain.Episode
import com.moviepicker.domain.Genres
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.RatingScale
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleDetails
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.domain.Video
import com.moviepicker.domain.parseDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FeedSection(val title: String, val subtitle: String) {
    DIGITAL("Цифровые релизы", "Фильмы, которые недавно вышли онлайн"),
    NEW_SERIES("Новые сериалы", "Премьеры последних трёх месяцев"),
    ON_AIR("Сериалы в эфире", "Сейчас выходят новые серии и сезоны"),
    UPCOMING_MOVIES("Скоро: фильмы", "Анонсы на полгода вперёд"),
    UPCOMING_SERIES("Скоро: сериалы", "Ближайшие премьеры сериалов"),
    TRENDING("В тренде", "Что смотрят на этой неделе"),
}

data class Page(val items: List<Title>, val page: Int, val hasMore: Boolean)

class TitleRepository(
    private val api: TmdbApi,
    private val dao: UserTitleStore,
    private val settings: SettingsStore,
    private val appScope: CoroutineScope,
) {

    // ---------- Ленты ----------

    suspend fun feed(section: FeedSection, page: Int = 1): Page {
        val today = LocalDate.now()
        val response = when (section) {
            FeedSection.DIGITAL -> api.discover(
                "movie", baseDiscover(page) + mapOf(
                    "with_release_type" to "4|5",
                    "release_date.gte" to today.minusDays(60).toString(),
                    "release_date.lte" to today.toString(),
                )
            )
            FeedSection.NEW_SERIES -> api.discover(
                "tv", baseDiscover(page) + tvExclusions + mapOf(
                    "first_air_date.gte" to today.minusDays(90).toString(),
                    "first_air_date.lte" to today.toString(),
                )
            )
            FeedSection.ON_AIR -> api.discover(
                "tv", baseDiscover(page) + tvExclusions + mapOf(
                    "air_date.gte" to today.minusDays(7).toString(),
                    "air_date.lte" to today.plusDays(7).toString(),
                )
            )
            FeedSection.UPCOMING_MOVIES -> api.discover(
                "movie", baseDiscover(page) + mapOf(
                    "primary_release_date.gte" to today.plusDays(1).toString(),
                    "primary_release_date.lte" to today.plusDays(180).toString(),
                )
            )
            FeedSection.UPCOMING_SERIES -> api.discover(
                "tv", baseDiscover(page) + tvExclusions + mapOf(
                    "first_air_date.gte" to today.plusDays(1).toString(),
                    "first_air_date.lte" to today.plusDays(180).toString(),
                )
            )
            FeedSection.TRENDING -> api.trending("all", "week", page)
        }
        val defaultType = when (section) {
            FeedSection.DIGITAL, FeedSection.UPCOMING_MOVIES, FeedSection.TRENDING -> MediaType.MOVIE
            else -> MediaType.TV
        }
        return Page(
            items = response.results.mapNotNull { it.toTitle(defaultType) },
            page = response.page,
            hasMore = response.page < response.totalPages.coerceAtMost(500),
        )
    }

    suspend fun discover(type: MediaType, params: Map<String, String>, page: Int = 1): List<Title> =
        api.discover(type.path, baseDiscover(page) + params).results.mapNotNull { it.toTitle(type) }

    suspend fun tmdbRecommendations(key: TitleKey): List<Title> =
        api.recommendations(key.type.path, key.id).results.mapNotNull { it.toTitle(key.type) }

    suspend fun trending(page: Int = 1): List<Title> =
        api.trending("all", "week", page).results.mapNotNull { it.toTitle(MediaType.MOVIE) }

    suspend fun search(query: String, page: Int = 1): Page {
        val response = api.searchMulti(query, page)
        return Page(
            items = response.results.mapNotNull { it.toTitle(null) },
            page = response.page,
            hasMore = response.page < response.totalPages,
        )
    }

    private fun baseDiscover(page: Int) = mapOf(
        "page" to page.toString(),
        "sort_by" to "popularity.desc",
        "include_adult" to "false",
    )

    /** Новости, ток-шоу и мыльные оперы в ленте сериалов только мешают. */
    private val tvExclusions = mapOf("without_genres" to "10763,10767,10766")

    // ---------- Детали ----------

    suspend fun details(key: TitleKey): TitleDetails {
        val append = when (key.type) {
            MediaType.MOVIE -> "credits,keywords,videos,release_dates,recommendations"
            MediaType.TV -> "credits,keywords,videos,recommendations"
        }
        val dto = api.details(key.type.path, key.id, append)
        val overviewFallback = if (dto.overview.isNullOrBlank()) {
            runCatching { api.details(key.type.path, key.id, append = null, language = "en-US").overview }
                .getOrNull()
        } else null
        return dto.toDetails(key.type, overviewFallback)
    }

    suspend fun videos(key: TitleKey): List<Video> =
        api.videos(key.type.path, key.id).results.toTrailers()

    // ---------- Оценки ----------

    fun observeMark(key: TitleKey): Flow<UserMark> =
        dao.observe(key.type.path, key.id).map { it.toMark() }

    fun observeMarks(): Flow<Map<TitleKey, UserMark>> =
        dao.observeAll().map { list -> list.associate { TitleKey(it.mediaType, it.id) to it.toMark() } }

    fun observeAll(): Flow<List<UserTitleEntity>> = dao.observeAll()

    suspend fun allMarked(): List<UserTitleEntity> = dao.getAll()

    /**
     * Лайк или «Не моё» в одно касание. Лайк означает «смотрел, понравилось»: тайтл попадает в дневник
     * и уходит из «Хочу посмотреть». «Не моё» без оценки — «не интересно», в дневник не попадает.
     * Оценка сохраняется, только если подходит к реакции (лайк — 6..10, «Не моё» — 1..5).
     * Дата в дневнике ставится сама — день, когда поставлен лайк.
     */
    suspend fun setReaction(title: Title, reaction: Reaction) {
        val base = dao.get(title.type.path, title.id) ?: UserTitleEntity.from(title)
        val rating = base.userRating.takeIf { it in RatingScale.range(reaction) } ?: 0
        val watched = reaction == Reaction.LIKE || rating > 0
        val sameMark = base.reaction == reaction.value && base.watchedAt != null
        save(
            base.copy(
                reaction = reaction.value,
                userRating = rating,
                watched = watched,
                watchedAt = if (!watched) null else if (sameMark) base.watchedAt else LocalDate.now().toString(),
                inWatchlist = base.inWatchlist && !watched,
                fromOnboarding = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Уточнение оценки 1..10 (0 — снять уточнение). Реакция следует за оценкой: 6+ — лайк, 1–5 — «Не моё».
     * Дата в дневнике — день, когда поставлена оценка.
     */
    suspend fun setRating(title: Title, rating: Int) {
        val base = dao.get(title.type.path, title.id) ?: UserTitleEntity.from(title)
        val r = rating.coerceIn(0, 10)
        val reaction = if (r > 0) RatingScale.reactionFor(r).value else base.reaction
        val watched = reaction > 0 || r > 0
        val changed = r != base.userRating || base.watchedAt == null
        save(
            base.copy(
                userRating = r,
                reaction = reaction,
                watched = watched,
                watchedAt = if (!watched) null else if (changed) LocalDate.now().toString() else base.watchedAt,
                inWatchlist = base.inWatchlist && !watched,
                fromOnboarding = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun toggleWatchlist(title: Title) {
        val base = dao.get(title.type.path, title.id) ?: UserTitleEntity.from(title)
        save(base.copy(inWatchlist = !base.inWatchlist, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun save(entity: UserTitleEntity) {
        if (entity.isBlank) {
            dao.delete(entity.type, entity.id)
            return
        }
        dao.upsert(entity)
        if (!entity.featuresLoaded) {
            appScope.launch { runCatching { loadFeatures(entity.type, entity.id) } }
        }
    }

    suspend fun updateEntity(entity: UserTitleEntity) = dao.upsert(entity)

    suspend fun clearAll() = dao.clear()

    /**
     * Добавляет записи из резервной копии. Если тайтл уже отмечен на телефоне,
     * побеждает более свежая отметка. Возвращает количество применённых записей.
     */
    suspend fun importEntities(entities: List<UserTitleEntity>): Int {
        var applied = 0
        entities.forEach { incoming ->
            if (MediaType.fromPath(incoming.type) == null) return@forEach
            val existing = dao.get(incoming.type, incoming.id)
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                dao.upsert(incoming)
                applied++
            }
        }
        return applied
    }

    /** Сохраняет результат онбординга, заменяя прошлые стартовые лайки. */
    suspend fun applyOnboarding(genres: Set<Int>, likes: List<Title>) {
        dao.getAll().filter { it.fromOnboarding }.forEach {
            if (it.inWatchlist || it.watched) dao.upsert(it.copy(reaction = 0, fromOnboarding = false))
            else dao.delete(it.type, it.id)
        }
        likes.forEach { title ->
            val existing = dao.get(title.type.path, title.id)
            // Ручные оценки пользователя не перетираем
            if (existing != null && existing.reaction != 0) return@forEach
            val entity = (existing ?: UserTitleEntity.from(title)).copy(reaction = 1, fromOnboarding = true)
            dao.upsert(entity)
            if (!entity.featuresLoaded) {
                appScope.launch { runCatching { loadFeatures(entity.type, entity.id) } }
            }
        }
        settings.completeOnboarding(genres)
    }

    /**
     * Догружает признаки (ключевые слова, актёры, режиссёры) и текущее состояние релиза,
     * чтобы рекомендации были точнее, а уведомления не приходили о том, что уже вышло.
     */
    suspend fun loadFeatures(type: String, id: Int) {
        val mediaType = MediaType.fromPath(type) ?: return
        val details = details(TitleKey(mediaType, id))
        val entity = dao.get(type, id) ?: return
        val today = LocalDate.now()
        val stage = when {
            entity.releaseNotifyStage > 0 -> entity.releaseNotifyStage
            mediaType == MediaType.MOVIE && details.digitalDate?.let { !it.isAfter(today) } == true -> 2
            mediaType == MediaType.MOVIE && details.theatricalDate?.let { !it.isAfter(today) } == true -> 1
            else -> 0
        }
        dao.upsert(
            entity.copy(
                featuresLoaded = true,
                keywordIds = details.keywordIds.joinToString(","),
                castIds = details.castIds.joinToString(","),
                directorIds = details.directorIds.joinToString(","),
                genreIds = details.title.genreIds.joinToString(",").ifEmpty { entity.genreIds },
                posterPath = entity.posterPath ?: details.title.posterPath,
                releaseNotifyStage = stage,
                lastKnownSeason = maxOf(entity.lastKnownSeason, airedSeason(details.lastEpisode, today)),
                lastKnownEpisode = maxOf(entity.lastKnownEpisode, airedEpisodeCode(details.lastEpisode, today)),
            )
        )
    }

    private fun airedSeason(episode: Episode?, today: LocalDate): Int =
        if (episode != null && episode.date?.isAfter(today) != true) episode.season else 0

    private fun airedEpisodeCode(episode: Episode?, today: LocalDate): Int =
        if (episode != null && episode.date?.isAfter(today) != true) episode.code else 0

    private fun UserTitleEntity?.toMark() =
        if (this == null) UserMark()
        else UserMark(reactionValue, inWatchlist, watched, userRating, parseDate(watchedAt))
}

// ---------- Маппинг DTO → домен ----------

fun TitleDto.toTitle(defaultType: MediaType?): Title? {
    val type = MediaType.fromPath(mediaType) ?: if (mediaType == null) defaultType else null
    if (type == null || adult) return null
    val displayName = (if (type == MediaType.MOVIE) title else name) ?: title ?: name ?: return null
    val original = (if (type == MediaType.MOVIE) originalTitle else originalName)
    return Title(
        id = id,
        type = type,
        name = displayName,
        originalName = original?.takeIf { it != displayName },
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        date = parseDate(if (type == MediaType.MOVIE) releaseDate else firstAirDate),
        rating = voteAverage,
        voteCount = voteCount,
        popularity = popularity,
        genreIds = genreIds,
    )
}

private fun EpisodeDto.toEpisode() = Episode(seasonNumber, episodeNumber, parseDate(airDate), name)

private fun DetailsDto.toDetails(type: MediaType, overviewFallback: String?): TitleDetails {
    val displayName = (if (type == MediaType.MOVIE) title else name) ?: title ?: name ?: ""
    val original = if (type == MediaType.MOVIE) originalTitle else originalName
    val title = Title(
        id = id,
        type = type,
        name = displayName,
        originalName = original?.takeIf { it != displayName },
        overview = overview?.takeIf { it.isNotBlank() } ?: overviewFallback.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        date = parseDate(if (type == MediaType.MOVIE) releaseDate else firstAirDate),
        rating = voteAverage,
        voteCount = voteCount,
        popularity = popularity,
        genreIds = genres.map { it.id },
    )

    val allReleases = releaseDates?.results.orEmpty().flatMap { it.releaseDates }
    fun earliest(vararg types: Int) = allReleases
        .filter { it.type in types }
        .mapNotNull { parseDate(it.releaseDate) }
        .minOrNull()

    val trailers = videos?.results.orEmpty().toTrailers()

    val directorsCrew = credits?.crew.orEmpty().filter { it.job == "Director" }
    val cast = credits?.cast.orEmpty().sortedBy { it.order }

    return TitleDetails(
        title = title,
        genres = genres.map { g -> g.name.replaceFirstChar { it.uppercase() } },
        tagline = tagline?.takeIf { it.isNotBlank() },
        runtimeMinutes = runtime?.takeIf { it > 0 } ?: episodeRunTime.firstOrNull(),
        seasons = numberOfSeasons,
        episodes = numberOfEpisodes,
        status = status,
        cast = cast.take(15).map { CastMember(it.id, it.name, it.character, it.profilePath) },
        directors = if (type == MediaType.MOVIE) directorsCrew.map { it.name } else createdBy.map { it.name },
        videos = trailers,
        theatricalDate = earliest(2, 3) ?: title.date,
        digitalDate = earliest(4),
        lastEpisode = lastEpisodeToAir?.toEpisode(),
        nextEpisode = nextEpisodeToAir?.toEpisode(),
        similar = recommendations?.results.orEmpty().mapNotNull { it.toTitle(type) },
        keywordIds = keywords?.all.orEmpty().take(20).map { it.id },
        castIds = cast.take(5).map { it.id },
        directorIds = if (type == MediaType.MOVIE) directorsCrew.map { it.id } else createdBy.map { it.id },
    )
}

/** Ролики YouTube: сначала трейлеры, затем тизеры; русские и официальные — выше. */
fun List<VideoDto>.toTrailers(): List<Video> = this
    .filter { it.site == "YouTube" && it.key.isNotBlank() && (it.type == "Trailer" || it.type == "Teaser") }
    .sortedWith(
        compareByDescending<VideoDto> { it.language == "ru" }
            .thenByDescending { it.type == "Trailer" }
            .thenByDescending { it.official }
    )
    .distinctBy { it.key }
    .map { Video(it.key, it.name, it.type, it.language) }

/** Жанры тайтла в «киношном» пространстве. */
val Title.normalizedGenres: List<Int> get() = Genres.normalize(type, genreIds)

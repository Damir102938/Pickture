package com.moviepicker.domain

import com.moviepicker.data.FeedSection
import com.moviepicker.data.TitleRepository
import com.moviepicker.data.local.SettingsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp

data class Recommendation(
    val title: Title,
    val score: Double,
    val matchPercent: Int?,
    val reason: String,
)

/**
 * Рекомендации считаются на телефоне:
 * 1. Кандидаты собираются из нескольких источников: «рекомендации TMDB» к понравившемуся,
 *    подборки по любимым жанрам, темам (ключевым словам), актёрам/режиссёрам и свежие новинки.
 * 2. Каждый кандидат оценивается: совпадение жанров, сколько понравившихся тайтлов на него «указывают»,
 *    рейтинг, свежесть и штраф за сходство с тем, что не понравилось.
 */
class RecommendationEngine(
    private val repository: TitleRepository,
    private val settings: SettingsStore,
) {
    private class Candidate(val title: Title) {
        var collab = 0.0
        var negative = 0.0
        var fresh = false
        val reasons = mutableListOf<Pair<Double, String>>()

        fun add(weight: Double, reason: String) {
            collab += weight
            reasons += weight to reason
        }
    }

    private val mutex = Mutex()
    private var cacheKey: Int? = null
    private var cacheTime = 0L
    private var cache: List<Recommendation> = emptyList()

    suspend fun profile(): TasteProfile = TasteProfile.build(repository.allMarked(), settings.current())

    /** [limit] — сколько рекомендаций каждого типа (фильмов и сериалов). */
    suspend fun recommend(force: Boolean = false, limit: Int = 60): List<Recommendation> = mutex.withLock {
        ensureFeatures()
        val entities = repository.allMarked()
        val currentSettings = settings.current()
        val profile = TasteProfile.build(entities, currentSettings)
        val key = (entities.map { listOf(it.type, it.id, it.reaction, it.inWatchlist) } to
            listOf(currentSettings.useOnboarding, currentSettings.favoriteGenres)).hashCode()
        val fresh = System.currentTimeMillis() - cacheTime < CACHE_TTL_MS
        if (!force && key == cacheKey && fresh) return cache

        val result = if (profile.isEmpty) emptyList() else compute(profile, limit)
        cache = result
        cacheKey = key
        cacheTime = System.currentTimeMillis()
        result
    }

    /** Догружает признаки для оценённых тайтлов, если раньше не получилось (например, не было сети). */
    private suspend fun ensureFeatures() {
        repository.allMarked()
            .filter { !it.featuresLoaded && it.reaction != 0 }
            .take(10)
            .forEach { runCatching { repository.loadFeatures(it.type, it.id) } }
    }

    private suspend fun compute(profile: TasteProfile, limit: Int): List<Recommendation> = coroutineScope {
        val today = LocalDate.now()
        val threeYearsAgo = today.minusYears(3).toString()
        val candidates = HashMap<TitleKey, Candidate>()
        fun candidate(t: Title) = candidates.getOrPut(t.key) { Candidate(t) }

        // Каждый источник — отдельный запрос + правило, как учесть его результаты.
        // Запросы идут параллельно, результаты сливаются последовательно (без гонок).
        // Упавшие источники просто пропускаем.
        val sources = mutableListOf<Pair<suspend () -> List<Title>, (Title) -> Unit>>()
        fun source(fetch: suspend () -> List<Title>, apply: (Title) -> Unit) {
            sources += fetch to apply
        }

        profile.liked.take(10).forEachIndexed { index, liked ->
            val weight = 1.0 - index * 0.05
            source({ repository.tmdbRecommendations(TitleKey(liked.mediaType, liked.id)) }) {
                candidate(it).add(weight, "Похоже на «${liked.name}»")
            }
        }
        profile.disliked.take(5).forEach { disliked ->
            source({ repository.tmdbRecommendations(TitleKey(disliked.mediaType, disliked.id)) }) {
                candidate(it).negative += 1.0
            }
        }

        val topGenres = profile.topGenres(3)
        if (topGenres.isNotEmpty()) {
            val names = topGenres.mapNotNull { Genres.name(MediaType.MOVIE, it) }.joinToString(", ").lowercase()
            val reason = "Любимые жанры: $names"
            source({
                repository.discover(
                    MediaType.MOVIE, mapOf(
                        "with_genres" to topGenres.joinToString("|"),
                        "vote_count.gte" to "100",
                        "primary_release_date.gte" to threeYearsAgo,
                    )
                )
            }) { candidate(it).add(0.3, reason) }

            val tvGenres = Genres.toTv(topGenres)
            if (tvGenres.isNotEmpty()) source({
                repository.discover(
                    MediaType.TV, mapOf(
                        "with_genres" to tvGenres.joinToString("|"),
                        "vote_count.gte" to "50",
                        "first_air_date.gte" to threeYearsAgo,
                        "without_genres" to "10763,10767,10766",
                    )
                )
            }) { candidate(it).add(0.3, reason) }
        }

        val topKeywords = profile.topKeywords(5)
        if (topKeywords.isNotEmpty()) {
            MediaType.entries.forEach { type ->
                source({
                    repository.discover(
                        type, mapOf("with_keywords" to topKeywords.joinToString("|"), "vote_count.gte" to "30")
                    )
                }) { candidate(it).add(0.5, "Близкие темы и сюжеты") }
            }
        }

        val topPeople = profile.topPeople(3)
        if (topPeople.isNotEmpty()) source({
            repository.discover(
                MediaType.MOVIE, mapOf("with_people" to topPeople.joinToString("|"), "vote_count.gte" to "30")
            )
        }) { candidate(it).add(0.5, "С любимыми актёрами и режиссёрами") }

        listOf(
            FeedSection.DIGITAL, FeedSection.UPCOMING_MOVIES,
            FeedSection.NEW_SERIES, FeedSection.ON_AIR, FeedSection.UPCOMING_SERIES,
        ).forEach { section ->
            source({ repository.feed(section).items }) { candidate(it).fresh = true }
        }

        val outcomes = sources.map { (fetch, _) -> async { runCatching { fetch() } } }.awaitAll()
        if (outcomes.all { it.isFailure }) throw outcomes.first().exceptionOrNull()!!
        outcomes.forEachIndexed { i, outcome ->
            val apply = sources[i].second
            outcome.getOrNull()?.forEach(apply)
        }

        // Лучшие фильмы и лучшие сериалы отбираются отдельно — в приложении это две вкладки
        val scored = candidates.values
            .asSequence()
            .filter { it.title.key !in profile.excluded }
            .mapNotNull { c -> score(c, profile, today) }
            .sortedByDescending { it.score }
            .toList()
        MediaType.entries.flatMap { type -> scored.filter { it.title.type == type }.take(limit) }
            .sortedByDescending { it.score }
    }

    private fun score(c: Candidate, profile: TasteProfile, today: LocalDate): Recommendation? {
        val t = c.title
        val genre = profile.genreMatch(t) ?: 0.5
        val collab = 1 - exp(-c.collab)
        val negative = 1 - exp(-c.negative)
        val quality = if (t.voteCount < 20) 0.55 else (t.rating / 10).coerceIn(0.0, 1.0)
        val recency = t.date?.let { date ->
            val days = abs(ChronoUnit.DAYS.between(date, today))
            when {
                days <= 180 -> 1.0
                days <= 3 * 365 -> 0.5
                else -> 0.2
            }
        } ?: 0.3

        // Явно чужое по жанрам и ничем не подкреплённое — отбрасываем
        if (genre < 0.35 && collab < 0.5) return null
        // Плохо оценённое зрителями — тоже
        if (t.voteCount >= 50 && t.rating < 5.5) return null

        val score = 0.42 * genre + 0.30 * collab + 0.16 * quality + 0.12 * recency - 0.40 * negative
        val reason = c.reasons.maxByOrNull { it.first }?.second
            ?: if (c.fresh) "Новинка в ваших жанрах" else "Может понравиться"
        return Recommendation(t, score, profile.matchPercent(t), reason)
    }

    fun invalidate() {
        cacheKey = null
    }

    private companion object {
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }
}

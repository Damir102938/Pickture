package com.moviepicker.domain

import com.moviepicker.data.local.Settings
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.data.local.toIntList
import com.moviepicker.data.normalizedGenres
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Профиль вкуса, собранный из лайков, дизлайков и стартовых предпочтений.
 * Веса жанров лежат в диапазоне [-1, 1]: 1 — самый любимый жанр, отрицательные — нелюбимые.
 */
class TasteProfile(
    val genreWeights: Map<Int, Double>,
    val keywordWeights: Map<Int, Double>,
    val peopleWeights: Map<Int, Double>,
    val liked: List<UserTitleEntity>,
    val disliked: List<UserTitleEntity>,
    /** Тайтлы, которые уже оценены или лежат в «хочу посмотреть» — их не рекомендуем. */
    val excluded: Set<TitleKey>,
    val dislikedKeys: Set<TitleKey>,
) {
    val isEmpty: Boolean get() = genreWeights.values.none { it > 0 } && liked.isEmpty()

    /** Насколько жанры тайтла совпадают со вкусом: 0..1, или null, если вкус ещё неизвестен. */
    fun genreMatch(title: Title): Double? {
        if (isEmpty) return null
        val genres = title.normalizedGenres
        if (genres.isEmpty()) return 0.5
        val weights = genres.map { genreWeights[it] ?: 0.0 }
        // Главное — лучший жанр тайтла, но средний тоже важен; нелюбимый жанр заметно штрафует.
        val s = (weights.max() + weights.average()) / 2 - 0.5 * max(0.0, -weights.min())
        return ((s + 1) / 2).coerceIn(0.0, 1.0)
    }

    fun matchPercent(title: Title): Int? =
        genreMatch(title)?.let { (it * 100).roundToInt().coerceIn(1, 99) }

    fun topGenres(n: Int): List<Int> =
        genreWeights.filterValues { it > 0.15 }.entries.sortedByDescending { it.value }.take(n).map { it.key }

    fun topKeywords(n: Int): List<Int> =
        keywordWeights.filterValues { it > 0 }.entries.sortedByDescending { it.value }.take(n).map { it.key }

    fun topPeople(n: Int): List<Int> =
        peopleWeights.filterValues { it > 0 }.entries.sortedByDescending { it.value }.take(n).map { it.key }

    companion object {
        val EMPTY = TasteProfile(emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList(), emptySet(), emptySet())

        fun build(entities: List<UserTitleEntity>, settings: Settings): TasteProfile {
            val counted = entities.filter { settings.useOnboarding || !it.fromOnboarding }
            val liked = counted.filter { it.reaction > 0 }.sortedByDescending { it.updatedAt }
            val disliked = counted.filter { it.reaction < 0 }.sortedByDescending { it.updatedAt }

            val pos = HashMap<Int, Double>()
            val neg = HashMap<Int, Double>()
            liked.forEach { e -> e.normalizedGenres().forEach { pos.merge(it, 1.0, Double::plus) } }
            disliked.forEach { e -> e.normalizedGenres().forEach { neg.merge(it, 1.0, Double::plus) } }
            if (settings.useOnboarding) {
                settings.favoriteGenres.forEach { pos.merge(it, 2.0, Double::plus) }
            }

            val maxPos = max(pos.values.maxOrNull() ?: 0.0, 1.0)
            val genreWeights = (pos.keys + neg.keys).associateWith { g ->
                val p = pos[g] ?: 0.0
                val n = neg[g] ?: 0.0
                // Доля «любви» к жанру минус доля разочарований в нём
                (p / maxPos - n / (p + n + 1)).coerceIn(-1.0, 1.0)
            }

            val keywords = HashMap<Int, Double>()
            val people = HashMap<Int, Double>()
            fun accumulate(e: UserTitleEntity, sign: Double) {
                e.keywordIds.toIntList().forEach { keywords.merge(it, sign, Double::plus) }
                e.castIds.toIntList().forEach { people.merge(it, sign, Double::plus) }
                e.directorIds.toIntList().forEach { people.merge(it, 1.5 * sign, Double::plus) }
            }
            liked.forEach { accumulate(it, 1.0) }
            disliked.forEach { accumulate(it, -1.0) }

            return TasteProfile(
                genreWeights = genreWeights,
                // Ключевое слово или человек из одного фильма — ещё не закономерность
                keywordWeights = keywords.filterValues { it >= 2 },
                peopleWeights = people.filterValues { it >= 2 },
                liked = liked,
                disliked = disliked,
                excluded = entities.filter { it.reaction != 0 || it.inWatchlist }
                    .map { TitleKey(it.mediaType, it.id) }.toSet(),
                dislikedKeys = entities.filter { it.reaction < 0 }.map { TitleKey(it.mediaType, it.id) }.toSet(),
            )
        }

        private fun UserTitleEntity.normalizedGenres(): List<Int> =
            Genres.normalize(mediaType, genreIds.toIntList())
    }
}

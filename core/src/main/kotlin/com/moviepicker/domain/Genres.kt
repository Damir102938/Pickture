package com.moviepicker.domain

/**
 * У фильмов и сериалов в TMDB разные наборы жанров. Вкус пользователя считается
 * в «киношном» пространстве жанров, а сериальные жанры отображаются в него.
 */
object Genres {

    data class Genre(val movieId: Int, val name: String, val tvIds: List<Int>)

    /** Жанры, которые можно выбрать при онбординге. */
    val selectable: List<Genre> = listOf(
        Genre(28, "Боевик", listOf(10759)),
        Genre(12, "Приключения", listOf(10759)),
        Genre(35, "Комедия", listOf(35)),
        Genre(18, "Драма", listOf(18)),
        Genre(878, "Фантастика", listOf(10765)),
        Genre(14, "Фэнтези", listOf(10765)),
        Genre(53, "Триллер", emptyList()),
        Genre(80, "Криминал", listOf(80)),
        Genre(9648, "Детектив", listOf(9648)),
        Genre(27, "Ужасы", emptyList()),
        Genre(10749, "Мелодрама", emptyList()),
        Genre(16, "Мультфильм", listOf(16)),
        Genre(10751, "Семейный", listOf(10751, 10762)),
        Genre(36, "История", emptyList()),
        Genre(10752, "Военный", listOf(10768)),
        Genre(99, "Документальный", listOf(99)),
        Genre(10402, "Музыка", emptyList()),
        Genre(37, "Вестерн", listOf(37)),
    )

    private val movieNames: Map<Int, String> = selectable.associate { it.movieId to it.name } + mapOf(10770 to "Телефильм")

    private val tvNames: Map<Int, String> = mapOf(
        10759 to "Боевик и приключения",
        16 to "Мультфильм",
        35 to "Комедия",
        80 to "Криминал",
        99 to "Документальный",
        18 to "Драма",
        10751 to "Семейный",
        10762 to "Детский",
        9648 to "Детектив",
        10763 to "Новости",
        10764 to "Реалити-шоу",
        10765 to "Фантастика и фэнтези",
        10766 to "Мыльная опера",
        10767 to "Ток-шоу",
        10768 to "Война и политика",
        37 to "Вестерн",
    )

    /** Сериальный жанр → киношные жанры. */
    private val tvToMovie: Map<Int, List<Int>> = mapOf(
        10759 to listOf(28, 12),
        10765 to listOf(878, 14),
        10768 to listOf(10752),
        10762 to listOf(10751),
    )

    fun name(type: MediaType, id: Int): String? =
        if (type == MediaType.MOVIE) movieNames[id] else tvNames[id]

    /** Переводит жанры тайтла в «киношное» пространство для подсчёта вкуса. */
    fun normalize(type: MediaType, ids: List<Int>): List<Int> =
        if (type == MediaType.MOVIE) ids
        else ids.flatMap { tvToMovie[it] ?: listOf(it) }.filter { it in movieNames }.distinct()

    /** Киношные жанры → сериальные (для запросов discover/tv). */
    fun toTv(movieIds: Collection<Int>): List<Int> =
        movieIds.flatMap { id -> selectable.firstOrNull { it.movieId == id }?.tvIds ?: emptyList() }.distinct()
}

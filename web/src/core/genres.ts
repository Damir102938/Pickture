import type { MediaType } from './models'

/**
 * У фильмов и сериалов в TMDB разные наборы жанров. Вкус пользователя считается
 * в «киношном» пространстве жанров, а сериальные жанры отображаются в него.
 * Повторяет core/.../domain/Genres.kt.
 */
export interface Genre {
  movieId: number
  name: string
  tvIds: number[]
}

/** Жанры, которые можно выбрать при онбординге. */
export const selectableGenres: Genre[] = [
  { movieId: 28, name: 'Боевик', tvIds: [10759] },
  { movieId: 12, name: 'Приключения', tvIds: [10759] },
  { movieId: 35, name: 'Комедия', tvIds: [35] },
  { movieId: 18, name: 'Драма', tvIds: [18] },
  { movieId: 878, name: 'Фантастика', tvIds: [10765] },
  { movieId: 14, name: 'Фэнтези', tvIds: [10765] },
  { movieId: 53, name: 'Триллер', tvIds: [] },
  { movieId: 80, name: 'Криминал', tvIds: [80] },
  { movieId: 9648, name: 'Детектив', tvIds: [9648] },
  { movieId: 27, name: 'Ужасы', tvIds: [] },
  { movieId: 10749, name: 'Мелодрама', tvIds: [] },
  { movieId: 16, name: 'Мультфильм', tvIds: [16] },
  { movieId: 10751, name: 'Семейный', tvIds: [10751, 10762] },
  { movieId: 36, name: 'История', tvIds: [] },
  { movieId: 10752, name: 'Военный', tvIds: [10768] },
  { movieId: 99, name: 'Документальный', tvIds: [99] },
  { movieId: 10402, name: 'Музыка', tvIds: [] },
  { movieId: 37, name: 'Вестерн', tvIds: [37] },
]

const movieNames = new Map<number, string>([
  ...selectableGenres.map((g) => [g.movieId, g.name] as [number, string]),
  [10770, 'Телефильм'],
])

const tvNames = new Map<number, string>([
  [10759, 'Боевик и приключения'],
  [16, 'Мультфильм'],
  [35, 'Комедия'],
  [80, 'Криминал'],
  [99, 'Документальный'],
  [18, 'Драма'],
  [10751, 'Семейный'],
  [10762, 'Детский'],
  [9648, 'Детектив'],
  [10763, 'Новости'],
  [10764, 'Реалити-шоу'],
  [10765, 'Фантастика и фэнтези'],
  [10766, 'Мыльная опера'],
  [10767, 'Ток-шоу'],
  [10768, 'Война и политика'],
  [37, 'Вестерн'],
])

/** Сериальный жанр → киношные жанры. */
const tvToMovie = new Map<number, number[]>([
  [10759, [28, 12]],
  [10765, [878, 14]],
  [10768, [10752]],
  [10762, [10751]],
])

export function genreName(type: MediaType, id: number): string | undefined {
  return type === 'movie' ? movieNames.get(id) : tvNames.get(id)
}

/** Переводит жанры тайтла в «киношное» пространство для подсчёта вкуса. */
export function normalizeGenres(type: MediaType, ids: number[]): number[] {
  if (type === 'movie') return ids
  const out = ids.flatMap((id) => tvToMovie.get(id) ?? [id]).filter((id) => movieNames.has(id))
  return Array.from(new Set(out))
}

/** Киношные жанры → сериальные (для запросов discover/tv). */
export function genresToTv(movieIds: Iterable<number>): number[] {
  const out: number[] = []
  for (const id of movieIds) {
    for (const tv of selectableGenres.find((g) => g.movieId === id)?.tvIds ?? []) out.push(tv)
  }
  return Array.from(new Set(out))
}

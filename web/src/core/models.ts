/** Модели — повторяют core/src/main/kotlin/com/moviepicker/domain/Models.kt. */

export type MediaType = 'movie' | 'tv'
export const MEDIA_TYPES: MediaType[] = ['movie', 'tv']

export function mediaTypeFromPath(path: string | null | undefined): MediaType | null {
  return path === 'movie' || path === 'tv' ? path : null
}

/** Краткая информация о фильме или сериале — то, что показывается в карточках. */
export interface Title {
  id: number
  type: MediaType
  name: string
  originalName: string | null
  overview: string
  posterPath: string | null
  backdropPath: string | null
  /** Дата в формате YYYY-MM-DD. */
  date: string | null
  rating: number
  voteCount: number
  popularity: number
  genreIds: number[]
}

export type TitleKey = string

export const keyOf = (type: MediaType, id: number): TitleKey => `${type}:${id}`
export const titleKey = (t: { type: MediaType; id: number }): TitleKey => keyOf(t.type, t.id)

export function parseKey(key: TitleKey): { type: MediaType; id: number } | null {
  const [type, id] = key.split(':')
  const t = mediaTypeFromPath(type)
  const n = Number(id)
  return t && Number.isInteger(n) ? { type: t, id: n } : null
}

export const REACTION = { NONE: 0, LIKE: 1, DISLIKE: -1 } as const

export interface UserMark {
  reaction: number
  inWatchlist: boolean
  watched: boolean
  /** Оценка 1..10, 0 — без оценки. */
  rating: number
  /** Дата просмотра, YYYY-MM-DD. */
  watchedAt: string | null
}

/**
 * Оценка уточняет лайк или «Не моё»: после лайка можно выбрать 6–10, после «Не моё» — 1–5.
 * Вес для вкуса: 9–10 — сильный плюс, 7–8 — плюс, 6 и 5 — почти нейтрально, 1–4 — минус.
 * Лайк без уточнения весит как «9», «Не моё» — как «2». Повторяет RatingScale из core/.../domain/Models.kt.
 */
export const RatingScale = {
  weight(rating: number): number {
    switch (rating) {
      case 10:
        return 1.25
      case 9:
        return 1.0
      case 8:
        return 0.65
      case 7:
        return 0.35
      case 6:
        return 0.15
      case 5:
        return -0.25
      case 4:
        return -0.5
      case 3:
        return -0.75
      case 2:
        return -1.0
      case 1:
        return -1.25
      default:
        return 0
    }
  },

  /** Реакция, которая следует за оценкой: 6+ — лайк, 1–5 — «Не моё». */
  reactionFor(rating: number): number {
    if (rating >= 6) return REACTION.LIKE
    if (rating >= 1 && rating <= 5) return REACTION.DISLIKE
    return REACTION.NONE
  },

  /** Какие оценки можно выбрать для уточнения: после лайка 6–10, после «Не моё» 1–5. */
  range(reaction: number): number[] {
    if (reaction === REACTION.LIKE) return [6, 7, 8, 9, 10]
    if (reaction === REACTION.DISLIKE) return [1, 2, 3, 4, 5]
    return []
  },

  label(rating: number): string {
    return (
      ['Без оценки', 'Ужасно', 'Очень плохо', 'Плохо', 'Слабо', 'Средне', 'Неплохо', 'Хорошо', 'Очень хорошо', 'Отлично', 'Шедевр'][
        rating
      ] ?? 'Без оценки'
    )
  },
}

export interface CastMember {
  id: number
  name: string
  character: string | null
  photoPath: string | null
}

export interface Video {
  key: string
  name: string
  type: string
  language: string | null
}

export function videoTypeLabel(type: string): string {
  switch (type) {
    case 'Trailer':
      return 'Трейлер'
    case 'Teaser':
      return 'Тизер'
    case 'Clip':
      return 'Отрывок'
    case 'Featurette':
      return 'О съёмках'
    case 'Behind the Scenes':
      return 'За кадром'
    default:
      return 'Видео'
  }
}

export interface Episode {
  season: number
  number: number
  date: string | null
  name: string | null
}

export interface TitleDetails {
  title: Title
  genres: string[]
  tagline: string | null
  runtimeMinutes: number | null
  seasons: number | null
  episodes: number | null
  status: string | null
  cast: CastMember[]
  directors: string[]
  videos: Video[]
  theatricalDate: string | null
  digitalDate: string | null
  lastEpisode: Episode | null
  nextEpisode: Episode | null
  similar: Title[]
  keywordIds: number[]
  castIds: number[]
  directorIds: number[]
}

export const Images = {
  base: 'https://image.tmdb.org/t/p/',
  poster: (path: string | null | undefined, size = 'w342') => (path ? `${Images.base}${size}${path}` : null),
  backdrop: (path: string | null | undefined, size = 'w780') => (path ? `${Images.base}${size}${path}` : null),
  profile: (path: string | null | undefined, size = 'w185') => (path ? `${Images.base}${size}${path}` : null),
}

// ---------- Даты (YYYY-MM-DD, по местному времени) ----------

export function parseDate(value: string | null | undefined): string | null {
  if (!value || value.trim() === '') return null
  const d = value.slice(0, 10)
  return /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : null
}

function pad(n: number) {
  return n < 10 ? '0' + n : String(n)
}

export function formatIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function today(): string {
  return formatIso(new Date())
}

export function plusDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  return formatIso(new Date(y, m - 1, d + days))
}

export function minusYears(iso: string, years: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  return formatIso(new Date(y - years, m - 1, d))
}

export function daysBetween(a: string, b: string): number {
  const [y1, m1, d1] = a.split('-').map(Number)
  const [y2, m2, d2] = b.split('-').map(Number)
  return Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86_400_000)
}

export const yearOf = (iso: string | null) => (iso ? Number(iso.slice(0, 4)) : null)

const MONTHS_FULL = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']
const MONTHS_SHORT = ['янв.', 'февр.', 'мар.', 'апр.', 'мая', 'июн.', 'июл.', 'авг.', 'сент.', 'окт.', 'нояб.', 'дек.']

export function formatFull(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return `${d} ${MONTHS_FULL[m - 1]} ${y}`
}

/** Подпись под карточкой: для будущих релизов — дата, для вышедших — год. */
export function titleSubtitle(t: Title): string {
  const now = today()
  const d = t.date
  let datePart: string
  if (!d) datePart = 'Дата неизвестна'
  else if (d > now) {
    const [y, m, day] = d.split('-').map(Number)
    datePart = y === Number(now.slice(0, 4)) ? `${day} ${MONTHS_SHORT[m - 1]}` : formatFull(d)
  } else datePart = d.slice(0, 4)
  return t.type === 'tv' ? `${datePart} · сериал` : datePart
}

export function formatRating(r: number): string {
  return r.toFixed(1)
}

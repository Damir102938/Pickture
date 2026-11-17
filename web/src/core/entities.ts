import { type MediaType, type Title, mediaTypeFromPath, parseDate } from './models'

/**
 * Всё, что пользователь отметил: лайк/дизлайк, «хочу посмотреть».
 * Поля и их имена совпадают с UserTitleEntity из core/.../data/local/UserTitles.kt —
 * объект целиком попадает в файл резервной копии, общий для Android, ПК и iPhone.
 */
export interface UserTitleEntity {
  id: number
  type: string
  name: string
  posterPath: string | null
  backdropPath: string | null
  date: string | null
  rating: number
  voteCount: number
  genreIds: string
  reaction: number
  inWatchlist: boolean
  /** Отмечено на стартовом экране (можно отключить в настройках). */
  fromOnboarding: boolean
  updatedAt: number
  featuresLoaded: boolean
  keywordIds: string
  castIds: string
  directorIds: string
  /** Для уведомлений Android: 0 — не было, 1 — сообщили о премьере, 2 — о цифровом релизе. */
  releaseNotifyStage: number
  lastKnownSeason: number
}

export interface Settings {
  onboardingDone: boolean
  /** Учитывать ли жанры и фильмы со стартового экрана в рекомендациях. */
  useOnboarding: boolean
  favoriteGenres: number[]
  tmdbApiKey: string
}

export const DEFAULT_SETTINGS: Settings = {
  onboardingDone: false,
  useOnboarding: true,
  favoriteGenres: [],
  tmdbApiKey: '',
}

export const entityMediaType = (e: UserTitleEntity): MediaType => mediaTypeFromPath(e.type) ?? 'movie'

export const entityKey = (e: { type: string; id: number }) => `${e.type}:${e.id}`

export function toIntList(s: string | null | undefined): number[] {
  if (!s) return []
  return s
    .split(',')
    .map((x) => x.trim())
    .filter((x) => /^-?\d+$/.test(x))
    .map(Number)
}

export function entityFromTitle(t: Title): UserTitleEntity {
  return {
    id: t.id,
    type: t.type,
    name: t.name,
    posterPath: t.posterPath,
    backdropPath: t.backdropPath,
    date: t.date,
    rating: t.rating,
    voteCount: t.voteCount,
    genreIds: t.genreIds.join(','),
    reaction: 0,
    inWatchlist: false,
    fromOnboarding: false,
    updatedAt: Date.now(),
    featuresLoaded: false,
    keywordIds: '',
    castIds: '',
    directorIds: '',
    releaseNotifyStage: 0,
    lastKnownSeason: 0,
  }
}

export function entityToTitle(e: UserTitleEntity): Title {
  return {
    id: e.id,
    type: entityMediaType(e),
    name: e.name,
    originalName: null,
    overview: '',
    posterPath: e.posterPath,
    backdropPath: e.backdropPath,
    date: parseDate(e.date),
    rating: e.rating,
    voteCount: e.voteCount,
    popularity: 0,
    genreIds: toIntList(e.genreIds),
  }
}

/**
 * Приводит запись из файла или хранилища к полному виду: недостающие поля
 * получают значения по умолчанию, как при чтении в Kotlin. null — запись непригодна.
 */
export function sanitizeEntity(raw: unknown): UserTitleEntity | null {
  if (!raw || typeof raw !== 'object') return null
  const r = raw as Record<string, unknown>
  const id = Number(r.id)
  const type = typeof r.type === 'string' ? r.type : ''
  if (!Number.isInteger(id) || !mediaTypeFromPath(type) || typeof r.name !== 'string') return null
  const str = (v: unknown) => (typeof v === 'string' ? v : null)
  const num = (v: unknown, d = 0) => (typeof v === 'number' && Number.isFinite(v) ? v : d)
  const bool = (v: unknown, d = false) => (typeof v === 'boolean' ? v : d)
  const ids = (v: unknown) => (typeof v === 'string' ? v : Array.isArray(v) ? v.join(',') : '')
  return {
    id,
    type,
    name: r.name,
    posterPath: str(r.posterPath),
    backdropPath: str(r.backdropPath),
    date: str(r.date),
    rating: num(r.rating),
    voteCount: Math.trunc(num(r.voteCount)),
    genreIds: ids(r.genreIds),
    reaction: Math.sign(num(r.reaction)),
    inWatchlist: bool(r.inWatchlist),
    fromOnboarding: bool(r.fromOnboarding),
    updatedAt: num(r.updatedAt, Date.now()),
    featuresLoaded: bool(r.featuresLoaded),
    keywordIds: ids(r.keywordIds),
    castIds: ids(r.castIds),
    directorIds: ids(r.directorIds),
    releaseNotifyStage: Math.trunc(num(r.releaseNotifyStage)),
    lastKnownSeason: Math.trunc(num(r.lastKnownSeason)),
  }
}

import { useEffect, useState } from 'preact/hooks'
import { computeRecommendations, type Recommendation } from '../core/engine'
import {
  DEFAULT_SETTINGS,
  type Settings,
  type UserTitleEntity,
  entityFromTitle,
  entityKey,
  isBlank,
  sanitizeEntity,
} from '../core/entities'
import {
  type Title,
  type TitleKey,
  type UserMark,
  REACTION,
  RatingScale,
  formatIso,
  mediaTypeFromPath,
  titleKey,
  today,
} from '../core/models'
import { TitleRepository } from '../core/repository'
import { TasteProfile } from '../core/taste'
import { TmdbApi } from '../core/tmdb'
import * as db from './db'

export interface AppState {
  loaded: boolean
  settings: Settings
  titles: Map<TitleKey, UserTitleEntity>
  profile: TasteProfile
  recs: db.RecsCache | null
  feed: db.FeedCache | null
  persisted: boolean | null
}

let state: AppState = {
  loaded: false,
  settings: DEFAULT_SETTINGS,
  titles: new Map(),
  profile: TasteProfile.EMPTY,
  recs: null,
  feed: null,
  persisted: null,
}

const listeners = new Set<() => void>()

export function getState() {
  return state
}

function update(patch: Partial<AppState>) {
  state = { ...state, ...patch }
  if (patch.titles || patch.settings) {
    state.profile = TasteProfile.build([...state.titles.values()], state.settings)
  }
  listeners.forEach((l) => l())
}

/** Подписка компонента на часть состояния. */
export function useApp<T>(select: (s: AppState) => T): T {
  const [value, setValue] = useState(() => select(state))
  useEffect(() => {
    const listener = () => setValue(() => select(state))
    listeners.add(listener)
    listener()
    return () => {
      listeners.delete(listener)
    }
  }, [])
  return value
}

export const api = new TmdbApi(() => state.settings.tmdbApiKey)
export const repository = new TitleRepository(api)

export async function init() {
  const data = await db.loadAll()
  update({
    loaded: true,
    settings: data.settings,
    titles: new Map(data.titles.map((e) => [entityKey(e), e])),
    recs: data.recs,
    feed: data.feed,
  })
  db.requestPersistence().then((persisted) => update({ persisted }))
}

// ---------- Настройки ----------

function setSettings(patch: Partial<Settings>) {
  const settings = { ...state.settings, ...patch }
  update({ settings })
  return db.saveSettings(settings)
}

export const setApiKey = (key: string) => setSettings({ tmdbApiKey: key.trim() })
export const setUseOnboarding = (value: boolean) => setSettings({ useOnboarding: value })
export const setRatingHintPending = (value: boolean) => setSettings({ ratingHintPending: value })
export const completeOnboarding = (genres: number[]) =>
  setSettings({ onboardingDone: true, favoriteGenres: Array.from(new Set(genres)) })

// ---------- Оценки ----------

export function markOf(key: TitleKey): UserMark | undefined {
  const e = state.titles.get(key)
  return e ? markFrom(e) : undefined
}

export function markFrom(e: UserTitleEntity): UserMark {
  return {
    reaction: e.reaction,
    inWatchlist: e.inWatchlist,
    watched: e.watched,
    rating: e.userRating,
    watchedAt: e.watchedAt,
  }
}

function commit(titles: Map<TitleKey, UserTitleEntity>) {
  update({ titles })
  return db.saveTitles([...titles.values()])
}

function save(entity: UserTitleEntity) {
  const titles = new Map(state.titles)
  const key = entityKey(entity)
  if (isBlank(entity)) {
    titles.delete(key)
    return commit(titles)
  }
  titles.set(key, entity)
  const done = commit(titles)
  if (!entity.featuresLoaded) loadFeatures(entity.type, entity.id).catch(() => {})
  return done
}

/**
 * Лайк или «Не моё» в одно касание. Лайк означает «смотрел, понравилось»: тайтл попадает в дневник
 * и уходит из «Хочу посмотреть». «Не моё» без оценки — «не интересно», в дневник не попадает.
 * Оценка сохраняется, только если подходит к реакции (лайк — 6..10, «Не моё» — 1..5).
 * Дата в дневнике ставится сама — день, когда поставлен лайк. Повторяет TitleRepository.setReaction.
 */
export function setReaction(title: Title, reaction: number) {
  const base = state.titles.get(titleKey(title)) ?? entityFromTitle(title)
  const userRating = RatingScale.range(reaction).includes(base.userRating) ? base.userRating : 0
  const watched = reaction === REACTION.LIKE || userRating > 0
  const sameMark = base.reaction === reaction && base.watchedAt != null
  return save({
    ...base,
    reaction,
    userRating,
    watched,
    watchedAt: !watched ? null : sameMark ? base.watchedAt : today(),
    inWatchlist: base.inWatchlist && !watched,
    fromOnboarding: false,
    updatedAt: Date.now(),
  })
}

/**
 * Уточнение оценки 1..10 (0 — снять уточнение). Реакция следует за оценкой: 6+ — лайк, 1–5 — «Не моё».
 * Дата в дневнике — день, когда поставлена оценка.
 */
export function setRating(title: Title, rating: number) {
  const base = state.titles.get(titleKey(title)) ?? entityFromTitle(title)
  const userRating = Math.min(10, Math.max(0, Math.trunc(rating)))
  const reaction = userRating > 0 ? RatingScale.reactionFor(userRating) : base.reaction
  const watched = reaction > 0 || userRating > 0
  const changed = userRating !== base.userRating || base.watchedAt == null
  return save({
    ...base,
    userRating,
    reaction,
    watched,
    watchedAt: !watched ? null : changed ? today() : base.watchedAt,
    inWatchlist: base.inWatchlist && !watched,
    fromOnboarding: false,
    updatedAt: Date.now(),
  })
}

export function toggleWatchlist(title: Title) {
  const base = state.titles.get(titleKey(title)) ?? entityFromTitle(title)
  return save({ ...base, inWatchlist: !base.inWatchlist, updatedAt: Date.now() })
}

/** Сохраняет результат онбординга, заменяя прошлые стартовые лайки. */
export async function applyOnboarding(genres: number[], likes: Title[]) {
  const firstRun = !state.settings.onboardingDone
  const titles = new Map(state.titles)
  for (const [key, e] of titles) {
    if (!e.fromOnboarding) continue
    if (e.inWatchlist || e.watched) titles.set(key, { ...e, reaction: 0, fromOnboarding: false })
    else titles.delete(key)
  }
  const toLoad: UserTitleEntity[] = []
  for (const title of likes) {
    const key = titleKey(title)
    const existing = titles.get(key)
    // Ручные оценки пользователя не перетираем
    if (existing && existing.reaction !== 0) continue
    const entity = { ...(existing ?? entityFromTitle(title)), reaction: REACTION.LIKE, fromOnboarding: true }
    titles.set(key, entity)
    if (!entity.featuresLoaded) toLoad.push(entity)
  }
  await commit(titles)
  await completeOnboarding(genres)
  // После первого стартового экрана подскажем, что отмеченное можно оценить точнее
  if (firstRun && likes.length > 0) await setSettings({ ratingHintPending: true })
  toLoad.forEach((e) => loadFeatures(e.type, e.id).catch(() => {}))
}

/**
 * Догружает признаки (ключевые слова, актёры, режиссёры) и состояние релиза,
 * чтобы рекомендации были точнее. Поля релиза нужны Android-версии после переноса копии.
 */
export async function loadFeatures(type: string, id: number) {
  const mediaType = mediaTypeFromPath(type)
  if (!mediaType) return
  const details = await repository.details(mediaType, id)
  const entity = state.titles.get(`${type}:${id}`)
  if (!entity) return
  const now = today()
  let stage = entity.releaseNotifyStage
  if (stage <= 0) {
    if (mediaType === 'movie' && details.digitalDate && details.digitalDate <= now) stage = 2
    else if (mediaType === 'movie' && details.theatricalDate && details.theatricalDate <= now) stage = 1
    else stage = 0
  }
  const last = details.lastEpisode
  const aired = !!last && !(last.date && last.date > now)
  const airedSeason = aired ? last.season : 0
  // Код серии: сезон * 1000 + номер — нужен Android-версии для уведомлений о новых сериях
  const airedEpisode = aired ? last.season * 1000 + last.number : 0
  const genreIds = details.title.genreIds.join(',')
  const titles = new Map(state.titles)
  titles.set(entityKey(entity), {
    ...entity,
    featuresLoaded: true,
    keywordIds: details.keywordIds.join(','),
    castIds: details.castIds.join(','),
    directorIds: details.directorIds.join(','),
    genreIds: genreIds || entity.genreIds,
    posterPath: entity.posterPath ?? details.title.posterPath,
    releaseNotifyStage: stage,
    lastKnownSeason: Math.max(entity.lastKnownSeason, airedSeason),
    lastKnownEpisode: Math.max(entity.lastKnownEpisode, airedEpisode),
  })
  await commit(titles)
}

export async function clearAll() {
  await commit(new Map())
  await invalidateRecs()
}

// ---------- Рекомендации ----------

const RECS_TTL_MS = 6 * 60 * 60 * 1000

function recsKey(): string {
  const entities = [...state.titles.values()]
    .map((e) => `${e.type}:${e.id}:${e.reaction}:${e.inWatchlist ? 1 : 0}:${e.watched ? 1 : 0}:${e.userRating}`)
    .sort()
  const s = state.settings
  return JSON.stringify([entities, s.useOnboarding, [...s.favoriteGenres].sort((a, b) => a - b)])
}

/** Догружает признаки для оценённых тайтлов, если раньше не получилось (например, не было сети). */
async function ensureFeatures() {
  const pending = [...state.titles.values()].filter((e) => !e.featuresLoaded && (e.reaction !== 0 || e.userRating > 0)).slice(0, 10)
  await Promise.allSettled(pending.map((e) => loadFeatures(e.type, e.id)))
}

let running: Promise<Recommendation[]> | null = null

/** Свежий ли сохранённый список: оценки не менялись и прошло меньше 6 часов. */
export function recsUpToDate(): boolean {
  const c = state.recs
  return !!c && c.key === recsKey() && Date.now() - c.time < RECS_TTL_MS
}

export function recommend(force = false): Promise<Recommendation[]> {
  if (!force && recsUpToDate()) return Promise.resolve(state.recs!.items)
  if (running) return running
  running = (async () => {
    try {
      await ensureFeatures()
      const key = recsKey()
      const items = await computeRecommendations(repository, state.profile, 60)
      const cache: db.RecsCache = { key, time: Date.now(), items }
      update({ recs: cache })
      await db.saveRecs(cache)
      return items
    } finally {
      running = null
    }
  })()
  return running
}

export async function invalidateRecs() {
  if (!state.recs) return
  const recs = { ...state.recs, time: 0 }
  update({ recs })
  await db.saveRecs(recs)
}

// ---------- Кэш ленты (для запуска без сети) ----------

export function saveFeedRows(rows: Record<string, Title[]>) {
  const feed: db.FeedCache = { time: Date.now(), rows: { ...(state.feed?.rows ?? {}), ...rows } }
  update({ feed })
  return db.saveFeed(feed)
}

// ---------- Резервная копия (формат core/.../data/Backup.kt) ----------

export const BACKUP_APP_ID = 'Pickture'

export class BackupError extends Error {}

function offsetDateTime(d: Date): string {
  const pad = (n: number, w = 2) => String(Math.abs(n)).padStart(w, '0')
  const offset = -d.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
  return `${formatIso(d)}T${time}${sign}${pad(Math.trunc(offset / 60))}:${pad(offset % 60)}`
}

export function backupFileName() {
  return `pickture-backup-${today()}.json`
}

/** Возвращает JSON и количество сохранённых тайтлов. */
export function exportJson(): { json: string; count: number } {
  const s = state.settings
  const titles = [...state.titles.values()].map((e) => sanitizeEntity(e)!).filter(Boolean)
  const backup = {
    app: BACKUP_APP_ID,
    version: 1,
    exported_at: offsetDateTime(new Date()),
    favorite_genres: [...s.favoriteGenres].sort((a, b) => a - b),
    use_onboarding: s.useOnboarding,
    titles,
  }
  return { json: JSON.stringify(backup, null, 4), count: titles.length }
}

/**
 * Добавляет записи из резервной копии. Если тайтл уже отмечен, побеждает более свежая отметка.
 */
export async function importJson(text: string): Promise<{ total: number; applied: number }> {
  let backup: Record<string, unknown>
  try {
    backup = JSON.parse(text.charCodeAt(0) === 0xfeff ? text.slice(1) : text)
  } catch {
    throw new BackupError('Это не файл резервной копии Pickture')
  }
  if (!backup || typeof backup !== 'object' || (backup.app ?? BACKUP_APP_ID) !== BACKUP_APP_ID) {
    throw new BackupError('Это не файл резервной копии Pickture')
  }
  const rawTitles = Array.isArray(backup.titles) ? backup.titles : []
  const titles = new Map(state.titles)
  let applied = 0
  for (const raw of rawTitles) {
    const incoming = sanitizeEntity(raw)
    if (!incoming) continue
    const key = entityKey(incoming)
    const existing = titles.get(key)
    if (!existing || incoming.updatedAt > existing.updatedAt) {
      titles.set(key, incoming)
      applied++
    }
  }
  await commit(titles)
  const genres = Array.isArray(backup.favorite_genres) ? backup.favorite_genres.filter(Number.isInteger) : []
  const useOnboarding = typeof backup.use_onboarding === 'boolean' ? backup.use_onboarding : true
  // Жанры объединяем, онбординг считаем пройденным
  await setSettings({
    useOnboarding,
    onboardingDone: true,
    favoriteGenres: Array.from(new Set([...state.settings.favoriteGenres, ...(genres as number[])])),
  })
  await invalidateRecs()
  return { total: rawTitles.length, applied }
}


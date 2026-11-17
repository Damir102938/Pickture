import { createStore, get, set } from 'idb-keyval'
import { DEFAULT_SETTINGS, type Settings, type UserTitleEntity, sanitizeEntity } from '../core/entities'
import type { Recommendation } from '../core/engine'
import type { Title } from '../core/models'

/**
 * Хранилище на устройстве. Основное — IndexedDB; настройки, ключ и оценки дублируются
 * в localStorage, чтобы данные пережили сбой одной из баз. При чтении побеждает
 * более свежая копия (по отметке savedAt).
 */

const idb = createStore('pickture', 'kv')

const LS_SETTINGS = 'pickture.settings'
const LS_TITLES = 'pickture.titles'

interface Stamped<T> {
  savedAt: number
  value: T
}

export interface RecsCache {
  key: string
  time: number
  items: Recommendation[]
}

export interface FeedCache {
  time: number
  rows: Record<string, Title[]>
}

export interface StoredData {
  settings: Settings
  titles: UserTitleEntity[]
  recs: RecsCache | null
  feed: FeedCache | null
}

function readLocal<T>(key: string): Stamped<T> | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as Stamped<T>) : null
  } catch {
    return null
  }
}

async function readIdb<T>(key: string): Promise<T | undefined> {
  try {
    return await get<T>(key, idb)
  } catch {
    return undefined
  }
}

function newest<T>(...copies: (Stamped<T> | null | undefined)[]): Stamped<T> | null {
  let best: Stamped<T> | null = null
  for (const c of copies) if (c && typeof c.savedAt === 'number' && (!best || c.savedAt > best.savedAt)) best = c
  return best
}

export async function loadAll(): Promise<StoredData> {
  const [settingsIdb, titlesIdb, recs, feed] = await Promise.all([
    readIdb<Stamped<Settings>>('settings'),
    readIdb<Stamped<UserTitleEntity[]>>('titles'),
    readIdb<RecsCache>('recs'),
    readIdb<FeedCache>('feed'),
  ])
  const settings = newest(settingsIdb, readLocal<Settings>(LS_SETTINGS))?.value
  const titles = newest(titlesIdb, readLocal<UserTitleEntity[]>(LS_TITLES))?.value
  return {
    settings: { ...DEFAULT_SETTINGS, ...(settings ?? {}) },
    titles: (Array.isArray(titles) ? titles : []).map(sanitizeEntity).filter((e): e is UserTitleEntity => e !== null),
    recs: recs ?? null,
    feed: feed ?? null,
  }
}

// Записи идут строго по очереди, чтобы старая версия не перезаписала новую.
let queue: Promise<unknown> = Promise.resolve()
function enqueue(task: () => Promise<unknown>) {
  queue = queue.then(task).catch((e) => console.warn('Не удалось сохранить данные', e))
  return queue
}

function writeLocal(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    console.warn('localStorage недоступен', e)
  }
}

export function saveSettings(settings: Settings) {
  const stamped: Stamped<Settings> = { savedAt: Date.now(), value: settings }
  writeLocal(LS_SETTINGS, stamped)
  return enqueue(() => set('settings', stamped, idb))
}

export function saveTitles(titles: UserTitleEntity[]) {
  const stamped: Stamped<UserTitleEntity[]> = { savedAt: Date.now(), value: titles }
  writeLocal(LS_TITLES, stamped)
  return enqueue(() => set('titles', stamped, idb))
}

export function saveRecs(cache: RecsCache | null) {
  return enqueue(() => set('recs', cache, idb))
}

export function saveFeed(cache: FeedCache) {
  return enqueue(() => set('feed', cache, idb))
}

/** Дожидается, пока все изменения запишутся (для тестов и перед перезагрузкой). */
export function flush() {
  return queue
}

/** Просит браузер не удалять данные при нехватке места. */
export async function requestPersistence(): Promise<boolean | null> {
  try {
    if (!navigator.storage?.persist) return null
    if (await navigator.storage.persisted()) return true
    return await navigator.storage.persist()
  } catch {
    return null
  }
}

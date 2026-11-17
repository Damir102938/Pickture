/** Клиент TMDB. Документация: https://developer.themoviedb.org/reference */

export const TMDB_API_URL = 'https://api.themoviedb.org/3/'
export const LANGUAGE = 'ru-RU'

export const TmdbLinks = {
  API_SETTINGS: 'https://www.themoviedb.org/settings/api',
  SIGN_UP: 'https://www.themoviedb.org/signup',
}

export class HttpError extends Error {
  constructor(public status: number) {
    super(`HTTP ${status}`)
  }
}

export class NetworkError extends Error {
  constructor() {
    super('network')
  }
}

type Params = Record<string, string | number | boolean | null | undefined>

/** Длинный Read Access Token (JWT) передаётся заголовком, короткий API key — параметром. */
function applyKey(key: string, url: URL, headers: Record<string, string>) {
  if (!key) return
  if (key.startsWith('eyJ')) headers.Authorization = `Bearer ${key}`
  else url.searchParams.set('api_key', key)
}

export class TmdbApi {
  constructor(private keyProvider: () => string) {}

  async get<T>(path: string, params: Params = {}): Promise<T> {
    const url = new URL(path, TMDB_API_URL)
    for (const [k, v] of Object.entries(params)) {
      if (v !== null && v !== undefined) url.searchParams.set(k, String(v))
    }
    if (!url.searchParams.has('language')) url.searchParams.set('language', LANGUAGE)
    const headers: Record<string, string> = { Accept: 'application/json' }
    applyKey(this.keyProvider().trim(), url, headers)
    let response: Response
    try {
      response = await fetch(url.toString(), { headers })
    } catch {
      throw new NetworkError()
    }
    if (!response.ok) throw new HttpError(response.status)
    return (await response.json()) as T
  }

  trending(type: string, window = 'week', page = 1) {
    return this.get<PagedResponse<TitleDto>>(`trending/${type}/${window}`, { page })
  }

  discover(type: string, params: Record<string, string>) {
    return this.get<PagedResponse<TitleDto>>(`discover/${type}`, params)
  }

  details(type: string, id: number, append: string | null, language: string | null = null) {
    return this.get<DetailsDto>(`${type}/${id}`, {
      append_to_response: append,
      include_video_language: 'ru,en,null',
      language,
    })
  }

  recommendations(type: string, id: number, page = 1) {
    return this.get<PagedResponse<TitleDto>>(`${type}/${id}/recommendations`, { page })
  }

  videos(type: string, id: number) {
    return this.get<VideosDto>(`${type}/${id}/videos`, { include_video_language: 'ru,en,null' })
  }

  searchMulti(query: string, page = 1) {
    return this.get<PagedResponse<TitleDto>>('search/multi', { query, page, include_adult: false })
  }
}

export type KeyCheck = { kind: 'valid' } | { kind: 'invalid' } | { kind: 'failed'; message: string }

/** Проверяет ключ одним лёгким запросом (configuration), прежде чем сохранить его. */
export async function checkKey(rawKey: string): Promise<KeyCheck> {
  const key = rawKey.trim()
  if (key.length < 20) return { kind: 'invalid' }
  const url = new URL('configuration', TMDB_API_URL)
  const headers: Record<string, string> = {}
  applyKey(key, url, headers)
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 15_000)
  try {
    // cache: 'no-store' — проверка не должна пройти по старому ответу из кэша
    const response = await fetch(url.toString(), { headers, cache: 'no-store', signal: controller.signal })
    if (response.ok) return { kind: 'valid' }
    if (response.status === 401) return { kind: 'invalid' }
    return { kind: 'failed', message: `TMDB ответил с ошибкой ${response.status}` }
  } catch {
    return { kind: 'failed', message: 'Нет связи с TMDB. Проверьте интернет; в некоторых регионах нужен VPN.' }
  } finally {
    clearTimeout(timer)
  }
}

export function userMessage(e: unknown): string {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 401:
        return 'TMDB отклонил ключ (401). Проверьте ключ в настройках.'
      case 404:
        return 'Не найдено (404).'
      case 429:
        return 'Слишком много запросов к TMDB, попробуйте через минуту.'
      default:
        return `Ошибка сервера TMDB (${e.status}).`
    }
  }
  if (e instanceof NetworkError) {
    return 'Нет связи с TMDB. Проверьте интернет; в некоторых регионах TMDB открывается только через VPN.'
  }
  return e instanceof Error && e.message ? e.message : 'Что-то пошло не так'
}

// ---------- DTO ----------

export interface PagedResponse<T> {
  page?: number
  results?: T[]
  total_pages?: number
  total_results?: number
}

export interface TitleDto {
  id: number
  media_type?: string | null
  title?: string | null
  name?: string | null
  original_title?: string | null
  original_name?: string | null
  overview?: string | null
  poster_path?: string | null
  backdrop_path?: string | null
  release_date?: string | null
  first_air_date?: string | null
  vote_average?: number
  vote_count?: number
  popularity?: number
  genre_ids?: number[]
  adult?: boolean
}

export interface VideoDto {
  key?: string
  site?: string
  type?: string
  name?: string
  official?: boolean
  iso_639_1?: string | null
}

export interface VideosDto {
  results?: VideoDto[]
}

export interface EpisodeDto {
  air_date?: string | null
  season_number?: number
  episode_number?: number
  name?: string | null
}

export interface DetailsDto {
  id: number
  title?: string | null
  name?: string | null
  original_title?: string | null
  original_name?: string | null
  overview?: string | null
  tagline?: string | null
  poster_path?: string | null
  backdrop_path?: string | null
  release_date?: string | null
  first_air_date?: string | null
  vote_average?: number
  vote_count?: number
  popularity?: number
  genres?: { id: number; name?: string }[]
  runtime?: number | null
  episode_run_time?: number[]
  number_of_seasons?: number | null
  number_of_episodes?: number | null
  status?: string | null
  last_episode_to_air?: EpisodeDto | null
  next_episode_to_air?: EpisodeDto | null
  created_by?: { id: number; name?: string }[]
  credits?: {
    cast?: { id: number; name?: string; character?: string | null; profile_path?: string | null; order?: number }[]
    crew?: { id: number; name?: string; job?: string | null }[]
  } | null
  keywords?: { keywords?: { id: number }[]; results?: { id: number }[] } | null
  videos?: VideosDto | null
  release_dates?: { results?: { iso_3166_1?: string; release_dates?: { type?: number; release_date?: string }[] }[] } | null
  recommendations?: PagedResponse<TitleDto> | null
}

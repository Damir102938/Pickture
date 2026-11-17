import {
  type Episode,
  type MediaType,
  type Title,
  type TitleDetails,
  type Video,
  mediaTypeFromPath,
  parseDate,
  plusDays,
  today,
} from './models'
import type { DetailsDto, EpisodeDto, TitleDto, TmdbApi, VideoDto } from './tmdb'

/** Разделы ленты — повторяют FeedSection из core/.../data/TitleRepository.kt. */
export type FeedSection = 'DIGITAL' | 'NEW_SERIES' | 'ON_AIR' | 'UPCOMING_MOVIES' | 'UPCOMING_SERIES' | 'TRENDING'

export const FEED_SECTIONS: { id: FeedSection; title: string; subtitle: string }[] = [
  { id: 'DIGITAL', title: 'Цифровые релизы', subtitle: 'Фильмы, которые недавно вышли онлайн' },
  { id: 'NEW_SERIES', title: 'Новые сериалы', subtitle: 'Премьеры последних трёх месяцев' },
  { id: 'ON_AIR', title: 'Сериалы в эфире', subtitle: 'Сейчас выходят новые серии и сезоны' },
  { id: 'UPCOMING_MOVIES', title: 'Скоро: фильмы', subtitle: 'Анонсы на полгода вперёд' },
  { id: 'UPCOMING_SERIES', title: 'Скоро: сериалы', subtitle: 'Ближайшие премьеры сериалов' },
  { id: 'TRENDING', title: 'В тренде', subtitle: 'Что смотрят на этой неделе' },
]

export const sectionInfo = (id: FeedSection) => FEED_SECTIONS.find((s) => s.id === id)!

export interface Page {
  items: Title[]
  page: number
  hasMore: boolean
}

/** Новости, ток-шоу и мыльные оперы в ленте сериалов только мешают. */
const tvExclusions = { without_genres: '10763,10767,10766' }

const baseDiscover = (page: number) => ({
  page: String(page),
  sort_by: 'popularity.desc',
  include_adult: 'false',
})

export class TitleRepository {
  constructor(private api: TmdbApi) {}

  async feed(section: FeedSection, page = 1): Promise<Page> {
    const now = today()
    const api = this.api
    let response
    switch (section) {
      case 'DIGITAL':
        response = await api.discover('movie', {
          ...baseDiscover(page),
          with_release_type: '4|5',
          'release_date.gte': plusDays(now, -60),
          'release_date.lte': now,
        })
        break
      case 'NEW_SERIES':
        response = await api.discover('tv', {
          ...baseDiscover(page),
          ...tvExclusions,
          'first_air_date.gte': plusDays(now, -90),
          'first_air_date.lte': now,
        })
        break
      case 'ON_AIR':
        response = await api.discover('tv', {
          ...baseDiscover(page),
          ...tvExclusions,
          'air_date.gte': plusDays(now, -7),
          'air_date.lte': plusDays(now, 7),
        })
        break
      case 'UPCOMING_MOVIES':
        response = await api.discover('movie', {
          ...baseDiscover(page),
          'primary_release_date.gte': plusDays(now, 1),
          'primary_release_date.lte': plusDays(now, 180),
        })
        break
      case 'UPCOMING_SERIES':
        response = await api.discover('tv', {
          ...baseDiscover(page),
          ...tvExclusions,
          'first_air_date.gte': plusDays(now, 1),
          'first_air_date.lte': plusDays(now, 180),
        })
        break
      case 'TRENDING':
        response = await api.trending('all', 'week', page)
        break
    }
    const defaultType: MediaType =
      section === 'DIGITAL' || section === 'UPCOMING_MOVIES' || section === 'TRENDING' ? 'movie' : 'tv'
    const current = response.page ?? 1
    return {
      items: mapTitles(response.results, defaultType),
      page: current,
      hasMore: current < Math.min(response.total_pages ?? 1, 500),
    }
  }

  async discover(type: MediaType, params: Record<string, string>, page = 1): Promise<Title[]> {
    const response = await this.api.discover(type, { ...baseDiscover(page), ...params })
    return mapTitles(response.results, type)
  }

  async tmdbRecommendations(type: MediaType, id: number): Promise<Title[]> {
    return mapTitles((await this.api.recommendations(type, id)).results, type)
  }

  async trending(page = 1): Promise<Title[]> {
    return mapTitles((await this.api.trending('all', 'week', page)).results, 'movie')
  }

  async search(query: string, page = 1): Promise<Page> {
    const response = await this.api.searchMulti(query, page)
    const current = response.page ?? 1
    return {
      items: mapTitles(response.results, null),
      page: current,
      hasMore: current < (response.total_pages ?? 1),
    }
  }

  async details(type: MediaType, id: number): Promise<TitleDetails> {
    const append =
      type === 'movie' ? 'credits,keywords,videos,release_dates,recommendations' : 'credits,keywords,videos,recommendations'
    const dto = await this.api.details(type, id, append)
    let overviewFallback: string | null = null
    if (!dto.overview || dto.overview.trim() === '') {
      try {
        overviewFallback = (await this.api.details(type, id, null, 'en-US')).overview ?? null
      } catch {
        overviewFallback = null
      }
    }
    return toDetails(dto, type, overviewFallback)
  }

  async videos(type: MediaType, id: number): Promise<Video[]> {
    return toTrailers((await this.api.videos(type, id)).results ?? [])
  }

}

// ---------- Маппинг DTO → домен ----------

function mapTitles(list: TitleDto[] | undefined, defaultType: MediaType | null): Title[] {
  return (list ?? []).map((dto) => toTitle(dto, defaultType)).filter((t): t is Title => t !== null)
}

export function toTitle(dto: TitleDto, defaultType: MediaType | null): Title | null {
  const type = mediaTypeFromPath(dto.media_type) ?? (dto.media_type == null ? defaultType : null)
  if (!type || dto.adult) return null
  const displayName = (type === 'movie' ? dto.title : dto.name) ?? dto.title ?? dto.name
  if (!displayName) return null
  const original = type === 'movie' ? dto.original_title : dto.original_name
  return {
    id: dto.id,
    type,
    name: displayName,
    originalName: original && original !== displayName ? original : null,
    overview: dto.overview ?? '',
    posterPath: dto.poster_path ?? null,
    backdropPath: dto.backdrop_path ?? null,
    date: parseDate(type === 'movie' ? dto.release_date : dto.first_air_date),
    rating: dto.vote_average ?? 0,
    voteCount: dto.vote_count ?? 0,
    popularity: dto.popularity ?? 0,
    genreIds: dto.genre_ids ?? [],
  }
}

function toEpisode(e: EpisodeDto | null | undefined): Episode | null {
  if (!e) return null
  return { season: e.season_number ?? 0, number: e.episode_number ?? 0, date: parseDate(e.air_date), name: e.name ?? null }
}

function capitalize(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function toDetails(dto: DetailsDto, type: MediaType, overviewFallback: string | null): TitleDetails {
  const displayName = (type === 'movie' ? dto.title : dto.name) ?? dto.title ?? dto.name ?? ''
  const original = type === 'movie' ? dto.original_title : dto.original_name
  const genres = dto.genres ?? []
  const title: Title = {
    id: dto.id,
    type,
    name: displayName,
    originalName: original && original !== displayName ? original : null,
    overview: dto.overview && dto.overview.trim() !== '' ? dto.overview : overviewFallback ?? '',
    posterPath: dto.poster_path ?? null,
    backdropPath: dto.backdrop_path ?? null,
    date: parseDate(type === 'movie' ? dto.release_date : dto.first_air_date),
    rating: dto.vote_average ?? 0,
    voteCount: dto.vote_count ?? 0,
    popularity: dto.popularity ?? 0,
    genreIds: genres.map((g) => g.id),
  }

  const allReleases = (dto.release_dates?.results ?? []).flatMap((c) => c.release_dates ?? [])
  const earliest = (...types: number[]) => {
    const dates = allReleases
      .filter((r) => types.includes(r.type ?? 0))
      .map((r) => parseDate(r.release_date))
      .filter((d): d is string => d !== null)
      .sort()
    return dates[0] ?? null
  }

  const crew = dto.credits?.crew ?? []
  const directorsCrew = crew.filter((c) => c.job === 'Director')
  const cast = [...(dto.credits?.cast ?? [])].sort((a, b) => (a.order ?? 999) - (b.order ?? 999))
  const creators = dto.created_by ?? []
  const keywords = [...(dto.keywords?.keywords ?? []), ...(dto.keywords?.results ?? [])]
  const runtime = dto.runtime && dto.runtime > 0 ? dto.runtime : dto.episode_run_time?.[0] ?? null
  const tagline = dto.tagline && dto.tagline.trim() !== '' ? dto.tagline : null

  return {
    title,
    genres: genres.map((g) => capitalize(g.name ?? '')),
    tagline,
    runtimeMinutes: runtime,
    seasons: dto.number_of_seasons ?? null,
    episodes: dto.number_of_episodes ?? null,
    status: dto.status ?? null,
    cast: cast.slice(0, 15).map((c) => ({
      id: c.id,
      name: c.name ?? '',
      character: c.character ?? null,
      photoPath: c.profile_path ?? null,
    })),
    directors: type === 'movie' ? directorsCrew.map((c) => c.name ?? '') : creators.map((c) => c.name ?? ''),
    videos: toTrailers(dto.videos?.results ?? []),
    theatricalDate: earliest(2, 3) ?? title.date,
    digitalDate: earliest(4),
    lastEpisode: toEpisode(dto.last_episode_to_air),
    nextEpisode: toEpisode(dto.next_episode_to_air),
    similar: mapTitles(dto.recommendations?.results, type),
    keywordIds: keywords.slice(0, 20).map((k) => k.id),
    castIds: cast.slice(0, 5).map((c) => c.id),
    directorIds: type === 'movie' ? directorsCrew.map((c) => c.id) : creators.map((c) => c.id),
  }
}

/** Ролики YouTube: сначала трейлеры, затем тизеры; русские и официальные — выше. */
export function toTrailers(list: VideoDto[]): Video[] {
  const filtered = list.filter(
    (v) => v.site === 'YouTube' && (v.key ?? '').trim() !== '' && (v.type === 'Trailer' || v.type === 'Teaser'),
  )
  const rank = (v: VideoDto) => (v.iso_639_1 === 'ru' ? 4 : 0) + (v.type === 'Trailer' ? 2 : 0) + (v.official ? 1 : 0)
  // Сортировка устойчивая, как sortedWith в Kotlin
  const sorted = filtered.map((v, i) => ({ v, i })).sort((a, b) => rank(b.v) - rank(a.v) || a.i - b.i)
  const seen = new Set<string>()
  const out: Video[] = []
  for (const { v } of sorted) {
    if (seen.has(v.key!)) continue
    seen.add(v.key!)
    out.push({ key: v.key!, name: v.name ?? '', type: v.type ?? '', language: v.iso_639_1 ?? null })
  }
  return out
}

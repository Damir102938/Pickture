import { genreName, genresToTv } from './genres'
import { entityMediaType as entityTypeOf } from './entities'
import { MEDIA_TYPES, type Title, type TitleKey, daysBetween, minusYears, titleKey, today } from './models'
import type { FeedSection, TitleRepository } from './repository'
import type { TasteProfile } from './taste'

export interface Recommendation {
  title: Title
  score: number
  matchPercent: number | null
  reason: string
}

class Candidate {
  collab = 0
  negative = 0
  fresh = false
  reasons: [number, string][] = []

  constructor(readonly title: Title) {}

  add(weight: number, reason: string) {
    this.collab += weight
    this.reasons.push([weight, reason])
  }
}

/**
 * Рекомендации считаются на устройстве — порт core/.../domain/RecommendationEngine.kt:
 * 1. Кандидаты собираются из нескольких источников: «рекомендации TMDB» к понравившемуся,
 *    подборки по любимым жанрам, темам (ключевым словам), актёрам/режиссёрам и свежие новинки.
 * 2. Каждый кандидат оценивается: совпадение жанров, сколько понравившихся тайтлов на него «указывают»,
 *    рейтинг, свежесть и штраф за сходство с тем, что не понравилось.
 *
 * @param limit сколько рекомендаций каждого типа (фильмов и сериалов)
 */
export async function computeRecommendations(
  repository: TitleRepository,
  profile: TasteProfile,
  limit = 60,
): Promise<Recommendation[]> {
  if (profile.isEmpty) return []
  const now = today()
  const threeYearsAgo = minusYears(now, 3)
  const candidates = new Map<TitleKey, Candidate>()
  const candidate = (t: Title) => {
    const key = titleKey(t)
    let c = candidates.get(key)
    if (!c) {
      c = new Candidate(t)
      candidates.set(key, c)
    }
    return c
  }

  // Каждый источник — отдельный запрос + правило, как учесть его результаты.
  // Запросы идут параллельно, результаты сливаются по порядку. Упавшие источники пропускаем.
  const sources: [() => Promise<Title[]>, (t: Title) => void][] = []
  const source = (fetch: () => Promise<Title[]>, apply: (t: Title) => void) => sources.push([fetch, apply])

  profile.liked.slice(0, 10).forEach((liked, index) => {
    const weight = 1.0 - index * 0.05
    source(
      () => repository.tmdbRecommendations(entityTypeOf(liked), liked.id),
      (t) => candidate(t).add(weight, `Похоже на «${liked.name}»`),
    )
  })
  profile.disliked.slice(0, 5).forEach((disliked) => {
    source(
      () => repository.tmdbRecommendations(entityTypeOf(disliked), disliked.id),
      (t) => {
        candidate(t).negative += 1.0
      },
    )
  })

  const topGenres = profile.topGenres(3)
  if (topGenres.length > 0) {
    const names = topGenres
      .map((g) => genreName('movie', g))
      .filter(Boolean)
      .join(', ')
      .toLowerCase()
    const reason = `Любимые жанры: ${names}`
    source(
      () =>
        repository.discover('movie', {
          with_genres: topGenres.join('|'),
          'vote_count.gte': '100',
          'primary_release_date.gte': threeYearsAgo,
        }),
      (t) => candidate(t).add(0.3, reason),
    )
    const tvGenres = genresToTv(topGenres)
    if (tvGenres.length > 0) {
      source(
        () =>
          repository.discover('tv', {
            with_genres: tvGenres.join('|'),
            'vote_count.gte': '50',
            'first_air_date.gte': threeYearsAgo,
            without_genres: '10763,10767,10766',
          }),
        (t) => candidate(t).add(0.3, reason),
      )
    }
  }

  const topKeywords = profile.topKeywords(5)
  if (topKeywords.length > 0) {
    for (const type of MEDIA_TYPES) {
      source(
        () => repository.discover(type, { with_keywords: topKeywords.join('|'), 'vote_count.gte': '30' }),
        (t) => candidate(t).add(0.5, 'Близкие темы и сюжеты'),
      )
    }
  }

  const topPeople = profile.topPeople(3)
  if (topPeople.length > 0) {
    source(
      () => repository.discover('movie', { with_people: topPeople.join('|'), 'vote_count.gte': '30' }),
      (t) => candidate(t).add(0.5, 'С любимыми актёрами и режиссёрами'),
    )
  }

  const freshSections: FeedSection[] = ['DIGITAL', 'UPCOMING_MOVIES', 'NEW_SERIES', 'ON_AIR', 'UPCOMING_SERIES']
  for (const section of freshSections) {
    source(
      async () => (await repository.feed(section)).items,
      (t) => {
        candidate(t).fresh = true
      },
    )
  }

  const outcomes = await Promise.allSettled(sources.map(([fetch]) => fetch()))
  if (outcomes.every((o) => o.status === 'rejected')) throw (outcomes[0] as PromiseRejectedResult).reason
  outcomes.forEach((outcome, i) => {
    if (outcome.status === 'fulfilled') outcome.value.forEach(sources[i][1])
  })

  // Лучшие фильмы и лучшие сериалы отбираются отдельно — в приложении это две вкладки
  const scored = [...candidates.values()]
    .filter((c) => !profile.excluded.has(titleKey(c.title)))
    .map((c) => score(c, profile, now))
    .filter((r): r is Recommendation => r !== null)
    .sort((a, b) => b.score - a.score)
  return MEDIA_TYPES.flatMap((type) => scored.filter((r) => r.title.type === type).slice(0, limit)).sort(
    (a, b) => b.score - a.score,
  )
}

function score(c: Candidate, profile: TasteProfile, now: string): Recommendation | null {
  const t = c.title
  const genre = profile.genreMatch(t) ?? 0.5
  const collab = 1 - Math.exp(-c.collab)
  const negative = 1 - Math.exp(-c.negative)
  const quality = t.voteCount < 20 ? 0.55 : Math.min(1, Math.max(0, t.rating / 10))
  let recency = 0.3
  if (t.date) {
    const days = Math.abs(daysBetween(t.date, now))
    recency = days <= 180 ? 1.0 : days <= 3 * 365 ? 0.5 : 0.2
  }

  // Явно чужое по жанрам и ничем не подкреплённое — отбрасываем
  if (genre < 0.35 && collab < 0.5) return null
  // Плохо оценённое зрителями — тоже
  if (t.voteCount >= 50 && t.rating < 5.5) return null

  const total = 0.42 * genre + 0.3 * collab + 0.16 * quality + 0.12 * recency - 0.4 * negative
  let best: [number, string] | null = null
  for (const r of c.reasons) if (!best || r[0] > best[0]) best = r
  const reason = best?.[1] ?? (c.fresh ? 'Новинка в ваших жанрах' : 'Может понравиться')
  return { title: t, score: total, matchPercent: profile.matchPercent(t), reason }
}

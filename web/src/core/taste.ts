import { type Settings, type UserTitleEntity, entityKey, entityMediaType, toIntList } from './entities'
import { normalizeGenres } from './genres'
import { RatingScale } from './models'
import type { Title, TitleKey } from './models'

/**
 * Профиль вкуса, собранный из лайков, дизлайков и стартовых предпочтений.
 * Веса жанров лежат в диапазоне [-1, 1]: 1 — самый любимый жанр, отрицательные — нелюбимые.
 * Повторяет core/.../domain/TasteProfile.kt.
 */
export class TasteProfile {
  constructor(
    readonly genreWeights: Map<number, number>,
    readonly keywordWeights: Map<number, number>,
    readonly peopleWeights: Map<number, number>,
    readonly liked: UserTitleEntity[],
    readonly disliked: UserTitleEntity[],
    /** Тайтлы, которые уже оценены или лежат в «хочу посмотреть» — их не рекомендуем. */
    readonly excluded: Set<TitleKey>,
    readonly dislikedKeys: Set<TitleKey>,
  ) {}

  get isEmpty(): boolean {
    for (const w of this.genreWeights.values()) if (w > 0) return false
    return this.liked.length === 0
  }

  /** Насколько жанры тайтла совпадают со вкусом: 0..1, или null, если вкус ещё неизвестен. */
  genreMatch(title: Title): number | null {
    if (this.isEmpty) return null
    const genres = normalizeGenres(title.type, title.genreIds)
    if (genres.length === 0) return 0.5
    const weights = genres.map((g) => this.genreWeights.get(g) ?? 0)
    const max = Math.max(...weights)
    const min = Math.min(...weights)
    const avg = weights.reduce((a, b) => a + b, 0) / weights.length
    // Главное — лучший жанр тайтла, но средний тоже важен; нелюбимый жанр заметно штрафует.
    const s = (max + avg) / 2 - 0.5 * Math.max(0, -min)
    return clamp((s + 1) / 2, 0, 1)
  }

  matchPercent(title: Title): number | null {
    const m = this.genreMatch(title)
    return m === null ? null : clamp(Math.round(m * 100), 1, 99)
  }

  topGenres(n: number): number[] {
    return top(this.genreWeights, n, (v) => v > 0.15)
  }

  topKeywords(n: number): number[] {
    return top(this.keywordWeights, n, (v) => v > 0)
  }

  topPeople(n: number): number[] {
    return top(this.peopleWeights, n, (v) => v > 0)
  }

  static readonly EMPTY = new TasteProfile(new Map(), new Map(), new Map(), [], [], new Set(), new Set())

  static build(entities: UserTitleEntity[], settings: Settings): TasteProfile {
    const counted = entities.filter((e) => settings.useOnboarding || !e.fromOnboarding)
    // Вес каждого тайтла: оценка 1..10 важнее лайка; 9–10 — сильный плюс, 1–4 — минус
    const weighted = counted.map((e) => [e, tasteWeight(e)] as const)
    const liked = weighted
      .filter(([, w]) => w > 0)
      .sort((a, b) => b[1] - a[1] || b[0].updatedAt - a[0].updatedAt)
      .map(([e]) => e)
    const disliked = weighted
      .filter(([, w]) => w < 0)
      .sort((a, b) => a[1] - b[1] || b[0].updatedAt - a[0].updatedAt)
      .map(([e]) => e)

    const pos = new Map<number, number>()
    const neg = new Map<number, number>()
    const merge = (m: Map<number, number>, k: number, v: number) => m.set(k, (m.get(k) ?? 0) + v)
    const genresOf = (e: UserTitleEntity) => normalizeGenres(entityMediaType(e), toIntList(e.genreIds))
    for (const [e, w] of weighted) {
      if (w > 0) genresOf(e).forEach((g) => merge(pos, g, w))
      else if (w < 0) genresOf(e).forEach((g) => merge(neg, g, -w))
    }
    if (settings.useOnboarding) settings.favoriteGenres.forEach((g) => merge(pos, g, 2))

    const maxPos = Math.max(pos.size ? Math.max(...pos.values()) : 0, 1)
    const genreWeights = new Map<number, number>()
    for (const g of new Set([...pos.keys(), ...neg.keys()])) {
      const p = pos.get(g) ?? 0
      const n = neg.get(g) ?? 0
      // Доля «любви» к жанру минус доля разочарований в нём
      genreWeights.set(g, clamp(p / maxPos - n / (p + n + 1), -1, 1))
    }

    const keywords = new Map<number, number>()
    const people = new Map<number, number>()
    for (const [e, w] of weighted) {
      if (w === 0) continue
      toIntList(e.keywordIds).forEach((k) => merge(keywords, k, w))
      toIntList(e.castIds).forEach((p) => merge(people, p, w))
      toIntList(e.directorIds).forEach((p) => merge(people, p, 1.5 * w))
    }

    // Ключевое слово или человек из одного фильма — ещё не закономерность
    const atLeastTwo = (m: Map<number, number>) => new Map([...m].filter(([, v]) => v >= 2))

    return new TasteProfile(
      genreWeights,
      atLeastTwo(keywords),
      atLeastTwo(people),
      liked,
      disliked,
      // Уже оценённое, просмотренное и отложенное не рекомендуем
      new Set(entities.filter((e) => e.reaction !== 0 || e.inWatchlist || e.watched).map(entityKey)),
      new Set(entities.filter((e) => tasteWeight(e) < 0).map(entityKey)),
    )
  }
}

/** Вес тайтла для профиля вкуса: оценка 1..10 по шкале, без оценки — лайк (+1) или дизлайк (−1). */
export function tasteWeight(e: UserTitleEntity): number {
  if (e.userRating > 0) return RatingScale.weight(e.userRating)
  if (e.reaction > 0) return 1
  if (e.reaction < 0) return -1
  return 0
}

export function clamp(v: number, min: number, max: number) {
  return Math.min(max, Math.max(min, v))
}

function top(m: Map<number, number>, n: number, keep: (v: number) => boolean): number[] {
  return [...m]
    .filter(([, v]) => keep(v))
    .sort((a, b) => b[1] - a[1])
    .slice(0, n)
    .map(([k]) => k)
}


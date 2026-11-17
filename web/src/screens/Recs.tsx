import { useEffect, useRef, useState } from 'preact/hooks'
import type { Recommendation } from '../core/engine'
import { type MediaType, REACTION, type Title, formatRating, titleKey, titleSubtitle } from '../core/models'
import { userMessage } from '../core/tmdb'
import { recommend, recsUpToDate, setReaction, toggleWatchlist, useApp } from '../data/store'
import {
  ErrorBox,
  LoadingBox,
  MessageBox,
  Poster,
  ScreenHeader,
  StampIconButton,
  TicketToggle,
  matchColor,
} from '../ui/components'
import { usePullToRefresh } from '../ui/gestures'
import { push, useNav } from '../ui/nav'
import { OfflineBanner } from './Home'
import { useScrollToTop } from './shared'

const TYPES: MediaType[] = ['movie', 'tv']

export function RecsScreen() {
  const recs = useApp((s) => s.recs)
  const profile = useApp((s) => s.profile)
  const nav = useNav()
  const active = nav.tab === 'recs' && nav.stack.length === 0
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  /** Оценённые прямо в списке — скрываем сразу, не дожидаясь пересчёта. */
  const [hidden, setHidden] = useState<Set<string>>(new Set())
  const [leaving, setLeaving] = useState<Set<string>>(new Set())
  const [tab, setTab] = useState<MediaType>('movie')
  const scroll = useRef<HTMLDivElement>(null)
  useScrollToTop(scroll, 'recs')

  async function load(force: boolean) {
    if (!force && recsUpToDate()) return
    setLoading(true)
    setError(null)
    try {
      await recommend(force)
      setHidden(new Set())
    } catch (e) {
      setError(userMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // При каждом возвращении на вкладку — пересчёт, если оценки изменились (иначе берётся сохранённое)
  useEffect(() => {
    if (active) load(false)
  }, [active])

  const hasData = recs !== null && recs.items !== undefined
  const ptr = usePullToRefresh(scroll, () => load(true), loading && hasData)

  function hide(t: Title, action: () => void) {
    const k = titleKey(t)
    setLeaving((s) => new Set(s).add(k))
    setTimeout(() => setHidden((s) => new Set(s).add(k)), 240)
    action()
  }

  const all: Recommendation[] = recs?.items ?? []
  // Уже оценённое скрываем и в сохранённой подборке (если пересчитать не удалось, например без сети)
  const visible = all.filter((r) => !hidden.has(titleKey(r.title)) && !profile.excluded.has(titleKey(r.title)))
  const items = visible.filter((r) => r.title.type === tab)

  return (
    <div class="scroll" ref={scroll}>
      {ptr}
      <ScreenHeader
        title="Для вас"
        subtitle="Подборка по вашим оценкам. Оценивайте прямо здесь — список станет точнее."
      />
      {!hasData && loading ? (
        <LoadingBox fill />
      ) : !hasData && error ? (
        <ErrorBox message={error} onRetry={() => load(true)} />
      ) : (
        <>
          {error && hasData && <OfflineBanner />}
          <TicketToggle
            options={TYPES.map(
              (type) => `${type === 'movie' ? 'Фильмы' : 'Сериалы'} · ${visible.filter((r) => r.title.type === type).length}`,
            )}
            selected={TYPES.indexOf(tab)}
            onSelect={(i) => setTab(TYPES[i])}
            style={{ margin: '8px 16px 16px' }}
          />
          {items.length === 0 &&
            (profile.isEmpty ? (
              <MessageBox
                title="Пока не на что опереться"
                text="Отметьте любимые жанры и пару фильмов или поставьте лайки в ленте новинок."
                actionLabel="Выбрать жанры и фильмы"
                onAction={() => push({ name: 'onboarding' })}
              />
            ) : loading ? (
              <LoadingBox />
            ) : (
              <MessageBox
                title="Рекомендации закончились"
                text="Потяните вниз, чтобы обновить, или оцените ещё несколько фильмов."
              />
            ))}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }} key={tab}>
            {items.map((rec, i) => (
              <RecommendationRow
                key={titleKey(rec.title)}
                rec={rec}
                index={i}
                gone={leaving.has(titleKey(rec.title))}
                onLike={() => hide(rec.title, () => setReaction(rec.title, REACTION.LIKE))}
                onDislike={() => hide(rec.title, () => setReaction(rec.title, REACTION.DISLIKE))}
                onWatchlist={() => hide(rec.title, () => toggleWatchlist(rec.title))}
              />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

/** Рекомендация — билет: постер, описание, линия отрыва и корешок с процентом совпадения. */
function RecommendationRow(props: {
  rec: Recommendation
  index: number
  gone: boolean
  onLike: () => void
  onDislike: () => void
  onWatchlist: () => void
}) {
  const t = props.rec.title
  const match = props.rec.matchPercent
  return (
    <div
      class={`rec-row ticket-v press${props.gone ? ' gone' : ''}`}
      style={{ animationDelay: `${Math.min(props.index, 8) * 30}ms` }}
      onClick={() => push({ name: 'details', type: t.type, id: t.id })}
      role="link"
    >
      <Poster path={t.posterPath} ratio={false} />
      <div class="body">
        <div class="name clamp2">{t.name}</div>
        <div class="t-small muted">
          {titleSubtitle(t)}
          {t.voteCount >= 5 && <span class="t-number gold">{'  '}★ {formatRating(t.rating)}</span>}
        </div>
        <div class="t-small accent clamp2" style={{ marginTop: 4 }}>
          {props.rec.reason}
        </div>
        <div class="actions">
          <StampIconButton icon="favoriteBorder" label="Нравится" onClick={props.onLike} color="var(--accent)" />
          <StampIconButton icon="thumbDownOutline" label="Не моё" onClick={props.onDislike} />
          <StampIconButton icon="bookmarkBorder" label="Хочу посмотреть" onClick={props.onWatchlist} color="var(--gold)" />
        </div>
      </div>
      <div class="perforation-v" />
      <div class="stub">
        {match !== null ? (
          <div style={{ color: matchColor(match), display: 'flex', alignItems: 'flex-end' }}>
            <span class="big">{match}</span>
            <span class="t-number" style={{ paddingBottom: 3 }}>
              %
            </span>
          </div>
        ) : (
          <span class="big muted">—</span>
        )}
        <span class="t-tiny muted">Совп.</span>
      </div>
    </div>
  )
}

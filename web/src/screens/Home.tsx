import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { type Title, titleKey } from '../core/models'
import { FEED_SECTIONS, type FeedSection } from '../core/repository'
import { userMessage } from '../core/tmdb'
import { getState, markOf, repository, saveFeedRows, useApp } from '../data/store'
import { LoadingBox, ErrorBox, ScreenHeader, SectionHeader, TextAction, TitleCard } from '../ui/components'
import { usePullToRefresh } from '../ui/gestures'
import { Icon } from '../ui/icons'
import { push } from '../ui/nav'
import { useScrollToTop } from './shared'

type Row = { state: 'loading' } | { state: 'error'; message: string } | { state: 'ok'; items: Title[] }

const openTitle = (t: Title) => push({ name: 'details', type: t.type, id: t.id })

export function HomeScreen() {
  const profile = useApp((s) => s.profile)
  useApp((s) => s.titles) // перерисовать отметки на постерах
  const [rows, setRows] = useState<Record<string, Row>>(() => {
    const cached = getState().feed?.rows ?? {}
    return Object.fromEntries(
      FEED_SECTIONS.map((s) => [s.id, cached[s.id] ? { state: 'ok', items: cached[s.id] } : { state: 'loading' }]),
    )
  })
  const [refreshing, setRefreshing] = useState(false)
  const [offline, setOffline] = useState(false)
  const scroll = useRef<HTMLDivElement>(null)
  useScrollToTop(scroll, 'home')

  async function load(refresh: boolean) {
    if (refresh) setRefreshing(true)
    const hasCache = getState().feed !== null
    let failed = 0
    const fresh: Record<string, Title[]> = {}
    await Promise.all(
      FEED_SECTIONS.map(async ({ id }) => {
        try {
          const page = await repository.feed(id)
          fresh[id] = page.items
          setRows((r) => ({ ...r, [id]: { state: 'ok', items: page.items } }))
        } catch (e) {
          failed++
          setRows((r) => (r[id]?.state === 'ok' ? r : { ...r, [id]: { state: 'error', message: userMessage(e) } }))
        }
      }),
    )
    if (Object.keys(fresh).length) saveFeedRows(fresh)
    setOffline(failed > 0 && hasCache)
    setRefreshing(false)
  }

  useEffect(() => {
    load(false)
  }, [])

  const ptr = usePullToRefresh(scroll, () => load(true), refreshing)

  /** Свежие релизы и анонсы, которые лучше всего совпадают со вкусом. */
  const forYou = useMemo(() => {
    if (profile.isEmpty) return []
    const seen = new Set<string>()
    const list: [Title, number][] = []
    for (const { id } of FEED_SECTIONS) {
      if (id === 'TRENDING') continue
      const row = rows[id]
      if (row?.state !== 'ok') continue
      for (const t of row.items) {
        const k = titleKey(t)
        if (seen.has(k) || profile.excluded.has(k)) continue
        seen.add(k)
        const m = profile.matchPercent(t)
        if (m !== null && m >= 70) list.push([t, m])
      }
    }
    return list
      .sort((a, b) => b[1] - a[1] || b[0].popularity - a[0].popularity)
      .slice(0, 20)
      .map(([t]) => t)
  }, [rows, profile])

  const allFailed = FEED_SECTIONS.every(({ id }) => rows[id]?.state === 'error')
  const offset = forYou.length ? 1 : 0

  return (
    <div class="scroll" ref={scroll}>
      {ptr}
      <ScreenHeader title="Что нового" subtitle="Свежие релизы, сериалы и анонсы" />
      {offline && <OfflineBanner />}
      {allFailed ? (
        <ErrorBox message={(rows[FEED_SECTIONS[0].id] as { message: string }).message} onRetry={() => load(false)} />
      ) : (
        <>
          {forYou.length > 0 && (
            <TitleRow
              // Подборка меняется, пока догружаются разделы: новая — с начала, а не с середины
              key={forYou.map(titleKey).join()}
              number={1}
              title="Новинки для вас"
              subtitle="Свежее, что совпадает с вашим вкусом"
              row={{ state: 'ok', items: forYou }}
              onRetry={() => {}}
            />
          )}
          {FEED_SECTIONS.map((s, i) => (
            <TitleRow
              key={s.id}
              number={i + 1 + offset}
              title={s.title}
              subtitle={s.subtitle}
              row={rows[s.id]}
              onAll={() => push({ name: 'section', section: s.id as FeedSection })}
              onRetry={() => load(false)}
            />
          ))}
        </>
      )}
    </div>
  )
}

export function OfflineBanner() {
  return (
    <div class="banner ticket" style={{ '--notch': '5px' }}>
      <Icon name="cloudOff" size={18} class="gold" />
      <span class="muted">Нет связи с TMDB — показано сохранённое. Потяните вниз, чтобы обновить.</span>
    </div>
  )
}

function TitleRow(props: {
  number: number
  title: string
  subtitle: string
  row: Row
  onAll?: () => void
  onRetry: () => void
}) {
  const profile = getState().profile
  const row = props.row
  return (
    <section style={{ paddingTop: 14 }}>
      <SectionHeader title={props.title} number={props.number} subtitle={props.subtitle} onAll={props.onAll} />
      <div style={{ height: 6 }} />
      {row.state === 'loading' ? (
        <LoadingBox />
      ) : row.state === 'error' ? (
        <div style={{ display: 'flex', alignItems: 'center', padding: '0 6px 0 16px' }}>
          <span class="t-small error" style={{ flex: 1 }}>
            {row.message}
          </span>
          <TextAction text="Повторить" onClick={props.onRetry} />
        </div>
      ) : row.items.length === 0 ? (
        <div class="muted" style={{ padding: '0 16px' }}>
          Пока ничего нет
        </div>
      ) : (
        <div class="row-scroll">
          {row.items.map((t) => (
            <TitleCard
              key={titleKey(t)}
              title={t}
              match={profile.matchPercent(t)}
              mark={markOf(titleKey(t))}
              onClick={() => openTitle(t)}
              width={118}
            />
          ))}
        </div>
      )}
    </section>
  )
}

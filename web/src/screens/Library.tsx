import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { type UserTitleEntity, entityToTitle, inDiary, isDisliked } from '../core/entities'
import { markOf, useApp } from '../data/store'
import { MessageBox, ScreenHeader, SeatTag, SectionHeader, TicketToggle, TitleGrid } from '../ui/components'
import { Icon, type IconName } from '../ui/icons'
import { useDesktop } from '../ui/layout'
import { push } from '../ui/nav'
import { DiaryList } from './Diary'
import { useScrollToTop } from './shared'

/** Сортировка списков в «Моих» (как в Android). */
type LibrarySort = 'date' | 'match' | 'tmdb' | 'mine'
const SORT_LABELS: Record<LibrarySort, string> = {
  date: 'По дате',
  match: 'По совпадению',
  tmdb: 'По TMDB',
  mine: 'По моей оценке',
}

const LIBRARY_TABS: {
  label: string
  icon: IconName
  empty: string
  sorts: LibrarySort[]
  filter: (e: UserTitleEntity) => boolean
}[] = [
  {
    label: 'Хочу посмотреть',
    icon: 'bookmark',
    empty: 'Добавляйте сюда фильмы, которые хотите посмотреть',
    sorts: ['date', 'match', 'tmdb'],
    filter: (e) => e.inWatchlist,
  },
  {
    label: 'Нравится',
    icon: 'favorite',
    empty: 'Ставьте «Нравится» в карточке фильма и при желании уточняйте оценку — так рекомендации станут точнее',
    sorts: ['date', 'mine'],
    filter: inDiary,
  },
  {
    label: 'Не моё',
    icon: 'thumbDown',
    empty: 'Здесь будет то, что вам не интересно или не понравилось (оценки 1–5)',
    sorts: ['date', 'mine'],
    filter: isDisliked,
  },
]

/** «По дате» — порядок уже задан (свежие отметки сверху); остальные — по убыванию, без значения — в конце. */
function sortEntities(list: UserTitleEntity[], sort: LibrarySort, matchOf: (e: UserTitleEntity) => number | null) {
  const by = (value: (e: UserTitleEntity) => number) => [...list].sort((a, b) => value(b) - value(a))
  switch (sort) {
    case 'match':
      return by((e) => matchOf(e) ?? -1)
    case 'tmdb':
      return by((e) => (e.voteCount > 0 ? e.rating : -1))
    case 'mine':
      return by((e) => e.userRating)
    default:
      return list
  }
}

function SortRow(props: { options: LibrarySort[]; selected: LibrarySort; onSelect: (s: LibrarySort) => void }) {
  return (
    <div class="sort-row">
      {props.options.map((s) => (
        <SeatTag key={s} text={SORT_LABELS[s]} selected={s === props.selected} onClick={() => props.onSelect(s)} />
      ))}
    </div>
  )
}

/** Индекс вкладки «Нравится». */
export const LIBRARY_LIKED_TAB = 1

let pendingTab: number | null = null

/** Открыть «Мои» сразу на нужной вкладке (например, из подсказки после стартового экрана). */
export function requestLibraryTab(index: number) {
  pendingTab = index
  window.dispatchEvent(new Event('pickture:library-tab'))
}

export function LibraryScreen() {
  const titles = useApp((s) => s.titles)
  const profile = useApp((s) => s.profile)
  const [tab, setTab] = useState(0)
  useEffect(() => {
    const take = () => {
      if (pendingTab === null) return
      setPrev(pendingTab)
      setTab(pendingTab)
      pendingTab = null
    }
    take()
    window.addEventListener('pickture:library-tab', take)
    return () => window.removeEventListener('pickture:library-tab', take)
  }, [])
  // Своя сортировка у каждой вкладки; по умолчанию — по дате
  const [sorts, setSorts] = useState<LibrarySort[]>(() => LIBRARY_TABS.map(() => 'date'))
  const [prev, setPrev] = useState(0)
  const scroll = useRef<HTMLDivElement>(null)
  const desktop = useDesktop()
  useScrollToTop(scroll, 'library')

  const entities = useMemo(() => [...titles.values()].sort((a, b) => b.updatedAt - a.updatedAt), [titles])
  const counts = LIBRARY_TABS.map((t) => entities.filter(t.filter).length)
  const current = LIBRARY_TABS[tab]
  const sort = sorts[tab]
  const filtered = useMemo(
    () => sortEntities(entities.filter(current.filter), sort, (e) => profile.matchPercent(entityToTitle(e))),
    [entities, tab, sort, profile],
  )
  const shown = useMemo(() => filtered.map(entityToTitle), [filtered])
  const sortRow =
    filtered.length > 1 ? (
      <SortRow
        options={current.sorts}
        selected={sort}
        onSelect={(s) => setSorts((all) => all.map((v, i) => (i === tab ? s : v)))}
      />
    ) : null

  return (
    <div class="scroll" ref={scroll}>
      <ScreenHeader
        title="Мои списки"
        trailing={
          !desktop && <button class="icon-square press" aria-label="Настройки" onClick={() => push({ name: 'settings' })}>
            <Icon name="settings" size={20} />
          </button>
        }
      />
      {/* Во вкладках только значок и число — полное название списка стоит заголовком ниже */}
      <TicketToggle
        options={counts.map((c) => (c > 0 ? String(c) : ''))}
        icons={LIBRARY_TABS.map((t) => t.icon)}
        descriptions={LIBRARY_TABS.map((t, i) => (counts[i] > 0 ? `${t.label}: ${counts[i]}` : t.label))}
        selected={tab}
        onSelect={(i) => {
          setPrev(tab)
          setTab(i)
        }}
        style={{ margin: '10px 16px 0' }}
      />
      <div key={tab} class={tab === prev ? '' : tab > prev ? 'slide-right' : 'slide-left'}>
        {current.filter === inDiary && shown.length > 0 ? (
          <>
            <div style={{ paddingTop: '12px' }}>
              <SectionHeader title={current.label} />
            </div>
            <DiaryList watched={filtered} byRating={sort === 'mine'} sortBar={sortRow} />
          </>
        ) : shown.length === 0 ? (
          <div style={{ paddingTop: '12px' }}>
            <SectionHeader title={current.label} />
            <MessageBox title="Пока пусто" text={current.empty} />
          </div>
        ) : (
          <TitleGrid
            items={shown}
            matchOf={(t) => profile.matchPercent(t)}
            markOf={markOf}
            onOpen={(t) => push({ name: 'details', type: t.type, id: t.id })}
            header={
              <>
                <SectionHeader title={current.label} inset={false} />
                {sortRow}
              </>
            }
          />
        )}
      </div>
    </div>
  )
}

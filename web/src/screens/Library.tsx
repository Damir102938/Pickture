import { useMemo, useRef, useState } from 'preact/hooks'
import { type UserTitleEntity, entityToTitle } from '../core/entities'
import { markOf, useApp } from '../data/store'
import { MessageBox, ScreenHeader, SectionHeader, TicketToggle, TitleGrid } from '../ui/components'
import { Icon, type IconName } from '../ui/icons'
import { useDesktop } from '../ui/layout'
import { push } from '../ui/nav'
import { useScrollToTop } from './shared'

const LIBRARY_TABS: { label: string; icon: IconName; empty: string; filter: (e: UserTitleEntity) => boolean }[] = [
  {
    label: 'Хочу посмотреть',
    icon: 'bookmark',
    empty: 'Добавляйте сюда фильмы, которые хотите посмотреть',
    filter: (e) => e.inWatchlist,
  },
  {
    label: 'Нравится',
    icon: 'favorite',
    empty: 'Ставьте лайки — так рекомендации станут точнее',
    filter: (e) => e.reaction > 0,
  },
  {
    label: 'Не моё',
    icon: 'thumbDown',
    empty: 'Здесь будет то, что вам не понравилось',
    filter: (e) => e.reaction < 0,
  },
]

export function LibraryScreen() {
  const titles = useApp((s) => s.titles)
  const profile = useApp((s) => s.profile)
  const [tab, setTab] = useState(0)
  const [prev, setPrev] = useState(0)
  const scroll = useRef<HTMLDivElement>(null)
  const desktop = useDesktop()
  useScrollToTop(scroll, 'library')

  const entities = useMemo(() => [...titles.values()].sort((a, b) => b.updatedAt - a.updatedAt), [titles])
  const counts = LIBRARY_TABS.map((t) => entities.filter(t.filter).length)
  const current = LIBRARY_TABS[tab]
  const shown = useMemo(() => entities.filter(current.filter).map(entityToTitle), [entities, tab])

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
        {shown.length === 0 ? (
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
            header={<SectionHeader title={current.label} inset={false} />}
          />
        )}
      </div>
    </div>
  )
}

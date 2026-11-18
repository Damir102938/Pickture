import { useEffect, useRef, useState } from 'preact/hooks'
import { setRatingHintPending, useApp } from './data/store'
import { isIos, isStandalone, updateApp, useUpdateAvailable } from './pwa'
import { ApiKeyScreen } from './screens/ApiKey'
import { DetailsScreen } from './screens/Details'
import { HomeScreen } from './screens/Home'
import { LIBRARY_LIKED_TAB, LibraryScreen, requestLibraryTab } from './screens/Library'
import { OnboardingScreen } from './screens/Onboarding'
import { RecsScreen } from './screens/Recs'
import { SearchScreen } from './screens/Search'
import { SectionScreen } from './screens/Section'
import { SettingsScreen } from './screens/Settings'
import { TrailerScreen } from './screens/Trailer'
import { MarqueeButton, MarqueeLoader, TextAction, TicketDialog, ToastHost } from './ui/components'
import { useSwipeBack } from './ui/gestures'
import { Icon, type IconName } from './ui/icons'
import { useDesktop } from './ui/layout'
import { DESKTOP_TABS, type StackEntry, TABS, type Tab, back, selectTab, useNav } from './ui/nav'

const TAB_INFO: Record<Tab, { label: string; icon: IconName }> = {
  home: { label: 'Новинки', icon: 'newReleases' },
  recs: { label: 'Для вас', icon: 'autoAwesome' },
  search: { label: 'Поиск', icon: 'search' },
  library: { label: 'Мои', icon: 'collections' },
  settings: { label: 'Настройки', icon: 'settings' },
}

function TabContent({ tab }: { tab: Tab }) {
  switch (tab) {
    case 'home':
      return <HomeScreen />
    case 'recs':
      return <RecsScreen />
    case 'search':
      return <SearchScreen />
    case 'library':
      return <LibraryScreen />
    case 'settings':
      return <SettingsScreen root />
  }
}

/** Вкладки открываются при первом заходе и дальше живут в фоне, чтобы не терять прокрутку и данные. */
function useVisited(tab: Tab) {
  const [visited, setVisited] = useState<Set<Tab>>(() => new Set([tab]))
  if (!visited.has(tab)) setVisited(new Set(visited).add(tab))
  return visited
}

export function App() {
  const loaded = useApp((s) => s.loaded)
  const hasKey = useApp((s) => s.settings.tmdbApiKey !== '')
  const onboardingDone = useApp((s) => s.settings.onboardingDone)

  if (!loaded) {
    return (
      <div class="splash">
        <span class="gold">
          <MarqueeLoader />
        </span>
      </div>
    )
  }
  if (!hasKey) {
    return (
      <div class="screen">
        <ApiKeyScreen onSaved={() => {}} />
        <ToastHost />
      </div>
    )
  }
  if (!onboardingDone) {
    return (
      <>
        <OnboardingScreen edit={false} onDone={() => {}} />
        <ToastHost />
      </>
    )
  }
  return <Main />
}

function Main() {
  const desktop = useDesktop()
  const nav = useNav()
  // На телефоне настройки — не вкладка, а экран внутри «Моих списков»
  useEffect(() => {
    if (!desktop && nav.tab === 'settings') selectTab('library')
  }, [desktop])
  return (
    <>
      {desktop ? <DesktopMain /> : <MobileMain />}
      <InstallHint />
      <RatingHint />
      <UpdateBar />
      <ToastHost />
    </>
  )
}

function MobileMain() {
  const nav = useNav()
  const visited = useVisited(nav.tab)
  const covered = nav.stack.length > 0
  const direction = TABS.indexOf(nav.tab) >= TABS.indexOf(nav.prevTab) ? 'enter-right' : 'enter-left'

  return (
    <>
      <div class={`tabs-layer${covered ? ' covered' : ''}`}>
        {TABS.filter((t) => visited.has(t)).map((tab) => (
          <div
            key={tab}
            class={`screen tab-screen${tab === nav.tab && nav.tab !== nav.prevTab ? ' ' + direction : ''}`}
            hidden={tab !== nav.tab}
          >
            <TabContent tab={tab} />
          </div>
        ))}
        <div class="status-scrim" />
        <TabBar />
      </div>
      {nav.stack.map((entry, i) => (
        <StackScreen key={entry.uid} entry={entry} covered={i < nav.stack.length - 1} />
      ))}
      {nav.leaving && <StackScreen key={nav.leaving.uid} entry={nav.leaving} leaving />}
    </>
  )
}

/**
 * ПК: слева — вертикальный билет с логотипом и вкладками, справа — экран.
 * Вложенные экраны открываются в той же области, лента остаётся на месте.
 */
function DesktopMain() {
  const nav = useNav()
  const visited = useVisited(nav.tab)
  const covered = nav.stack.length > 0
  const direction = DESKTOP_TABS.indexOf(nav.tab) >= DESKTOP_TABS.indexOf(nav.prevTab) ? 'enter-down' : 'enter-up'

  return (
    <div class="desk">
      <Rail />
      <main class="desk-content">
        {DESKTOP_TABS.filter((t) => visited.has(t)).map((tab) => (
          <div
            key={tab}
            class={`screen tab-screen${tab === nav.tab && nav.tab !== nav.prevTab ? ' ' + direction : ''}${covered ? ' desk-covered' : ''}`}
            hidden={tab !== nav.tab}
          >
            <TabContent tab={tab} />
          </div>
        ))}
        {nav.stack.map((entry, i) => (
          <StackScreen key={entry.uid} entry={entry} covered={i < nav.stack.length - 1} desktop />
        ))}
        {nav.leaving && <StackScreen key={nav.leaving.uid} entry={nav.leaving} leaving desktop />}
      </main>
    </div>
  )
}

/** Боковая навигация ПК — вертикальный билет: сверху корешок с названием, ниже вкладки. */
function Rail() {
  const nav = useNav()
  const index = DESKTOP_TABS.indexOf(nav.tab)
  const wobble = index >= DESKTOP_TABS.indexOf(nav.prevTab) ? 'wobble-r' : 'wobble-l'
  return (
    <nav class="rail ticket" role="tablist" aria-label="Разделы">
      <button class="rail-logo" onClick={() => selectTab('home')} aria-label="Pickture — новинки">
        <span class="rail-wordmark">Pickture</span>
      </button>
      <div class="perforation" />
      <div class="rail-items">
        <div
          key={nav.tab}
          class={`rail-stamp${nav.tab !== nav.prevTab ? ' ' + wobble : ''}`}
          style={{ transform: `translateY(${index * 100}%)` }}
        >
          <div />
        </div>
        {DESKTOP_TABS.map((tab) => (
          <button
            key={tab}
            role="tab"
            aria-selected={tab === nav.tab}
            class={`rail-item press${tab === nav.tab ? ' active' : ''}`}
            onClick={() => selectTab(tab)}
          >
            <Icon name={TAB_INFO[tab].icon} size={22} />
            <span class="t-tiny">{TAB_INFO[tab].label}</span>
          </button>
        ))}
      </div>
    </nav>
  )
}

function TabBar() {
  const nav = useNav()
  const index = TABS.indexOf(nav.tab)
  const wobble = TABS.indexOf(nav.tab) >= TABS.indexOf(nav.prevTab) ? 'wobble-r' : 'wobble-l'
  return (
    <nav class="tabbar-wrap">
      <div class="tabbar ticket" role="tablist">
        <div
          key={nav.tab}
          class={`stamp-indicator${nav.tab !== nav.prevTab ? ' ' + wobble : ''}`}
          style={{ transform: `translateX(${index * 100}%)` }}
        >
          <div />
        </div>
        {TABS.map((tab) => (
          <button
            key={tab}
            role="tab"
            aria-selected={tab === nav.tab}
            class={`tab-item press${tab === nav.tab ? ' active' : ''}`}
            onClick={() => selectTab(tab)}
          >
            <Icon name={TAB_INFO[tab].icon} size={20} />
            <span class="t-tiny">{TAB_INFO[tab].label}</span>
          </button>
        ))}
      </div>
    </nav>
  )
}

function StackScreen(props: { entry: StackEntry; covered?: boolean; leaving?: boolean; desktop?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  useSwipeBack(ref, !props.desktop && !props.leaving && !props.covered)
  const r = props.entry.route
  let content
  switch (r.name) {
    case 'details':
      content = <DetailsScreen type={r.type} id={r.id} />
      break
    case 'section':
      content = <SectionScreen section={r.section} />
      break
    case 'settings':
      content = <SettingsScreen />
      break
    case 'apikey':
      content = <ApiKeyScreen onSaved={back} onBack={back} />
      break
    case 'onboarding':
      content = <OnboardingScreen edit onDone={back} />
      break
    case 'trailer':
      content = <TrailerScreen type={r.type} id={r.id} video={r.video} />
      break
  }
  return (
    <div
      ref={ref}
      class={`screen ${props.desktop ? 'desk-stack' : 'stack-screen'}${props.leaving ? ' leaving' : ' entering'}${props.covered ? ' covered' : ''}`}
    >
      {content}
    </div>
  )
}

function UpdateBar() {
  const available = useUpdateAvailable()
  const [dismissed, setDismissed] = useState(false)
  if (!available || dismissed) return null
  return (
    <div class="toast" style={{ display: 'flex', alignItems: 'center', gap: '4px', padding: '4px 4px 4px 14px' }}>
      <span style={{ whiteSpace: 'nowrap' }}>Вышла новая версия</span>
      <TextAction text="Обновить" onClick={updateApp} />
      <TextAction text="Позже" muted onClick={() => setDismissed(true)} />
    </div>
  )
}

/** В Safari (не с экрана «Домой») подсказываем, как установить приложение. */
function InstallHint() {
  const [hidden, setHidden] = useState(() => localStorage.getItem('pickture.installHintHidden') === '1')
  if (hidden || !isIos() || isStandalone()) return null
  return (
    <div class="toast" style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 4px 8px 14px' }}>
      <Icon name="share" size={18} class="gold" />
      <span style={{ flex: 1 }}>
        Установите Pickture: «Поделиться» → «На экран Домой». Данные в Safari и на экране «Домой» хранятся отдельно.
      </span>
      <button
        class="icon-btn press"
        style={{ width: '36px', height: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        aria-label="Скрыть"
        onClick={() => {
          localStorage.setItem('pickture.installHintHidden', '1')
          setHidden(true)
        }}
      >
        <Icon name="close" size={18} />
      </button>
    </div>
  )
}

/** Подсказка после первого стартового экрана: отмеченное пока без оценки — где и как её поставить. Как в Android. */
function RatingHint() {
  const pending = useApp((s) => s.settings.ratingHintPending)
  const nav = useNav()
  if (!pending || nav.tab !== 'home' || nav.stack.length > 0) return null
  const later = () => setRatingHintPending(false)
  const open = () => {
    setRatingHintPending(false)
    requestLibraryTab(LIBRARY_LIKED_TAB)
    selectTab('library')
  }
  const steps = [
    'Откройте «Мои» → вкладку с сердцем «Нравится».',
    'Нажмите на фильм.',
    'Под кнопкой «Нравится» выберите оценку от 6 до 10.',
  ]
  return (
    <TicketDialog
      title="Оцените отмеченное"
      onDismiss={later}
      actions={
        <>
          <TextAction text="Позже" muted onClick={later} />
          <MarqueeButton text="Открыть «Нравится»" onClick={open} />
        </>
      }
    >
      <p style={{ margin: '0 0 12px' }}>
        Фильмы, которые вы отметили на стартовом экране, сейчас просто «нравятся» — без оценки. Если уточнить оценку,
        рекомендации станут точнее.
      </p>
      <div class="t-tiny gold" style={{ marginBottom: '6px' }}>
        Как это сделать
      </div>
      {steps.map((step, i) => (
        <div key={i} style={{ display: 'flex', gap: '8px', padding: '3px 0' }}>
          <span class="t-number accent" style={{ width: '22px', flex: 'none' }}>
            {String(i + 1).padStart(2, '0')}
          </span>
          <span>{step}</span>
        </div>
      ))}
      <div class="t-small muted" style={{ marginTop: '10px' }}>
        Так же можно оценить любой фильм прямо в его карточке.
      </div>
    </TicketDialog>
  )
}

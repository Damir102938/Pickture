import { useRef, useState } from 'preact/hooks'
import { useApp } from './data/store'
import { isIos, isStandalone, updateApp, useUpdateAvailable } from './pwa'
import { ApiKeyScreen } from './screens/ApiKey'
import { DetailsScreen } from './screens/Details'
import { HomeScreen } from './screens/Home'
import { LibraryScreen } from './screens/Library'
import { OnboardingScreen } from './screens/Onboarding'
import { RecsScreen } from './screens/Recs'
import { SearchScreen } from './screens/Search'
import { SectionScreen } from './screens/Section'
import { SettingsScreen } from './screens/Settings'
import { TrailerScreen } from './screens/Trailer'
import { MarqueeLoader, TextAction, ToastHost } from './ui/components'
import { useSwipeBack } from './ui/gestures'
import { Icon, type IconName } from './ui/icons'
import { type StackEntry, TABS, type Tab, back, selectTab, useNav } from './ui/nav'

const TAB_INFO: Record<Tab, { label: string; icon: IconName }> = {
  home: { label: 'Новинки', icon: 'newReleases' },
  recs: { label: 'Для вас', icon: 'autoAwesome' },
  search: { label: 'Поиск', icon: 'search' },
  library: { label: 'Мои', icon: 'collections' },
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
  const nav = useNav()
  const [visited, setVisited] = useState<Set<Tab>>(() => new Set([nav.tab]))
  if (!visited.has(nav.tab)) setVisited(new Set(visited).add(nav.tab))
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
            {tab === 'home' && <HomeScreen />}
            {tab === 'recs' && <RecsScreen />}
            {tab === 'search' && <SearchScreen />}
            {tab === 'library' && <LibraryScreen />}
          </div>
        ))}
        <div class="status-scrim" />
        <TabBar />
      </div>
      {nav.stack.map((entry, i) => (
        <StackScreen key={entry.uid} entry={entry} covered={i < nav.stack.length - 1} />
      ))}
      {nav.leaving && <StackScreen key={nav.leaving.uid} entry={nav.leaving} leaving />}
      <InstallHint />
      <UpdateBar />
      <ToastHost />
    </>
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

function StackScreen(props: { entry: StackEntry; covered?: boolean; leaving?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  useSwipeBack(ref, !props.leaving && !props.covered)
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
      class={`screen stack-screen${props.leaving ? ' leaving' : ' entering'}${props.covered ? ' covered' : ''}`}
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

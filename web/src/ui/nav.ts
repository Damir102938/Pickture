import { useEffect, useState } from 'preact/hooks'
import type { MediaType } from '../core/models'
import type { FeedSection } from '../core/repository'

export type Tab = 'home' | 'recs' | 'search' | 'library'
export const TABS: Tab[] = ['home', 'recs', 'search', 'library']

export type Route =
  | { name: 'details'; type: MediaType; id: number }
  | { name: 'section'; section: FeedSection }
  | { name: 'settings' }
  | { name: 'apikey' }
  | { name: 'onboarding' }
  | { name: 'trailer'; type: MediaType; id: number; video: string }

export interface StackEntry {
  uid: number
  route: Route
}

export interface NavState {
  tab: Tab
  /** Предыдущая вкладка — для направления анимации. */
  prevTab: Tab
  stack: StackEntry[]
  /** Экран, который сейчас уезжает вправо (анимация «назад»). */
  leaving: StackEntry | null
  /** Сколько раз нажали на уже открытую вкладку — экран прокручивается наверх. */
  reselect: number
}

let nav: NavState = { tab: 'home', prevTab: 'home', stack: [], leaving: null, reselect: 0 }
const listeners = new Set<() => void>()
let uid = 1
let leaveTimer: ReturnType<typeof setTimeout> | undefined
let skipAnimation = false

function set(patch: Partial<NavState>) {
  nav = { ...nav, ...patch }
  listeners.forEach((l) => l())
}

export function useNav(): NavState {
  const [, force] = useState(0)
  useEffect(() => {
    const l = () => force((n) => n + 1)
    listeners.add(l)
    return () => {
      listeners.delete(l)
    }
  }, [])
  return nav
}

export function selectTab(tab: Tab) {
  if (tab === nav.tab) set({ reselect: nav.reselect + 1 })
  else set({ prevTab: nav.tab, tab })
}

/** Открывает вложенный экран; кнопка/жест «назад» возвращают через историю браузера. */
export function push(route: Route) {
  const stack = [...nav.stack, { uid: uid++, route }]
  history.pushState({ depth: stack.length }, '')
  set({ stack })
}

export function back() {
  if (nav.stack.length === 0) return
  history.back()
}

/** Назад после жеста: экран уже уехал за палец, повторная анимация не нужна. */
export function backAfterSwipe() {
  skipAnimation = true
  back()
}

function popTo(depth: number) {
  if (depth >= nav.stack.length) return
  const removed = nav.stack[nav.stack.length - 1]
  const stack = nav.stack.slice(0, depth)
  clearTimeout(leaveTimer)
  if (skipAnimation) {
    skipAnimation = false
    set({ stack, leaving: null })
    return
  }
  set({ stack, leaving: removed })
  leaveTimer = setTimeout(() => set({ leaving: null }), 420)
}

/** Полный сброс (например, после смены ключа или онбординга). */
export function resetStack() {
  const depth = nav.stack.length
  if (depth > 0) history.go(-depth)
}

export function initNavigation() {
  history.replaceState({ depth: 0 }, '')
  window.addEventListener('popstate', (e) => {
    const depth = typeof e.state?.depth === 'number' ? e.state.depth : 0
    popTo(depth)
  })
}

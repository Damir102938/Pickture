import { useEffect, useState } from 'preact/hooks'

/**
 * ПК-раскладка — широкое окно и мышь. Телефоны и планшеты (в том числе в горизонтальном положении)
 * получают мобильную раскладку; узкое окно браузера на ПК — тоже.
 */
const query = '(min-width: 900px) and (pointer: fine)'

function matches(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.(query).matches
}

let desktop = matches()
const listeners = new Set<() => void>()

function apply() {
  document.documentElement.classList.toggle('desktop', desktop)
}

export function initLayout() {
  apply()
  const media = window.matchMedia?.(query)
  media?.addEventListener('change', () => {
    desktop = media.matches
    apply()
    listeners.forEach((l) => l())
  })
}

export function isDesktop() {
  return desktop
}

export function useDesktop(): boolean {
  const [value, setValue] = useState(desktop)
  useEffect(() => {
    const l = () => setValue(desktop)
    listeners.add(l)
    l()
    return () => {
      listeners.delete(l)
    }
  }, [])
  return value
}

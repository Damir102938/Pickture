import { useEffect, useState } from 'preact/hooks'

/** Открыто ли приложение с экрана «Домой» (а не во вкладке Safari). */
export function isStandalone(): boolean {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

export function isIos(): boolean {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

let updateReady = false
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

export function useUpdateAvailable(): boolean {
  const [, force] = useState(0)
  useEffect(() => {
    const l = () => force((n) => n + 1)
    listeners.add(l)
    return () => {
      listeners.delete(l)
    }
  }, [])
  return updateReady
}

/** Перезапускает приложение на новой версии. */
export function updateApp() {
  location.reload()
}

export function registerServiceWorker() {
  if (!('serviceWorker' in navigator) || import.meta.env.DEV) return
  // Если страница уже работала под service worker'ом, смена означает, что включилась новая версия
  let hadController = !!navigator.serviceWorker.controller
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (hadController) {
      updateReady = true
      notify()
    }
    hadController = true
  })
  const start = async () => {
    try {
      const registration = await navigator.serviceWorker.register('./sw.js', { scope: './' })
      // Старые версии ждали команды, прежде чем включиться, — даём её, если такая версия ждёт
      registration.waiting?.postMessage('skipWaiting')
      registration.update().catch(() => {})
      // Проверяем обновления при возвращении в приложение
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') registration.update().catch(() => {})
      })
    } catch (e) {
      console.warn('Service worker не зарегистрирован', e)
    }
  }
  if (document.readyState === 'complete') start()
  else window.addEventListener('load', start, { once: true })
}

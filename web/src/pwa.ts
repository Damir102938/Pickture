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

let waiting: ServiceWorker | null = null
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
  return waiting !== null
}

/** Ставит скачанную новую версию и перезапускает страницу. */
export function updateApp() {
  if (!waiting) return
  navigator.serviceWorker.addEventListener('controllerchange', () => location.reload(), { once: true })
  waiting.postMessage('skipWaiting')
}

export function registerServiceWorker() {
  if (!('serviceWorker' in navigator) || import.meta.env.DEV) return
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('./sw.js', { scope: './' })
      const track = (worker: ServiceWorker | null) => {
        if (!worker) return
        worker.addEventListener('statechange', () => {
          // Новая версия скачана, а страница работает на старой — предлагаем обновиться
          if (worker.state === 'installed' && navigator.serviceWorker.controller) {
            waiting = worker
            notify()
          }
        })
      }
      if (registration.waiting && navigator.serviceWorker.controller) {
        waiting = registration.waiting
        notify()
      }
      // Установка новой версии могла начаться ещё до подписки на updatefound
      track(registration.installing)
      registration.addEventListener('updatefound', () => track(registration.installing))
      // Проверяем обновления при возвращении в приложение
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') registration.update().catch(() => {})
      })
    } catch (e) {
      console.warn('Service worker не зарегистрирован', e)
    }
  })
}

/* Service worker Pickture. Собирается плагином в vite.config.ts: сюда подставляются версия и список файлов. */
const BUILD = '__BUILD__'
const PRECACHE = __PRECACHE__

const SHELL_CACHE = 'pickture-shell-' + BUILD
const API_CACHE = 'pickture-tmdb'
const IMAGE_CACHE = 'pickture-images'
const API_LIMIT = 400
const IMAGE_LIMIT = 800
const API_TIMEOUT_MS = 8000

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) => cache.addAll(PRECACHE.map((url) => new Request(url, { cache: 'reload' })))),
  )
  // Первая установка активируется сразу; обновление ждёт, пока пользователь нажмёт «Обновить».
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const names = await caches.keys()
      await Promise.all(
        names.filter((n) => n.startsWith('pickture-shell-') && n !== SHELL_CACHE).map((n) => caches.delete(n)),
      )
      await self.clients.claim()
    })(),
  )
})

self.addEventListener('message', (event) => {
  if (event.data === 'skipWaiting') self.skipWaiting()
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)

  if (url.hostname === 'api.themoviedb.org') {
    event.respondWith(networkFirst(request))
    return
  }
  if (url.hostname === 'image.tmdb.org') {
    event.respondWith(cacheFirst(request, IMAGE_CACHE, IMAGE_LIMIT))
    return
  }
  if (url.origin === self.location.origin) {
    event.respondWith(shell(request))
  }
})

/** Оболочка приложения: из кэша, а если файла там нет — из сети. Страница всегда открывается из кэша. */
async function shell(request) {
  const cache = await caches.open(SHELL_CACHE)
  if (request.mode === 'navigate') {
    const cached = (await cache.match('./')) || (await cache.match('./index.html'))
    if (cached) return cached
  }
  const cached = await cache.match(request, { ignoreSearch: true })
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok && new URL(request.url).pathname.includes('/assets/')) cache.put(request, response.clone())
  return response
}

/** Запросы к TMDB: сначала сеть, без сети — последний сохранённый ответ. */
async function networkFirst(request) {
  const cache = await caches.open(API_CACHE)
  try {
    const response = await withTimeout(fetch(request), API_TIMEOUT_MS)
    if (response.ok) {
      await cache.put(request, response.clone())
      trim(API_CACHE, API_LIMIT)
    }
    return response
  } catch (error) {
    const cached = await cache.match(request, { ignoreVary: true })
    if (cached) return cached
    throw error
  }
}

/** Постеры не меняются: берём из кэша, а новые сохраняем. */
async function cacheFirst(request, name, limit) {
  const cache = await caches.open(name)
  const cached = await cache.match(request, { ignoreVary: true })
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok || response.type === 'opaque') {
    await cache.put(request, response.clone())
    trim(name, limit)
  }
  return response
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), ms)
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (error) => {
        clearTimeout(timer)
        reject(error)
      },
    )
  })
}

let trimming = false
let puts = 0
/** Удаляет самые старые записи. Проверяем не на каждую запись, а раз в 25 — cache.keys() недешёвый. */
async function trim(name, limit) {
  if (trimming || ++puts % 25 !== 0) return
  trimming = true
  try {
    const cache = await caches.open(name)
    const keys = await cache.keys()
    const extra = keys.length - limit
    for (let i = 0; i < extra; i++) await cache.delete(keys[i])
  } finally {
    trimming = false
  }
}

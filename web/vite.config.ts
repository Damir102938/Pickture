import { defineConfig, type Plugin } from 'vite'
import preact from '@preact/preset-vite'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { IncomingMessage, ServerResponse } from 'node:http'

/** Версия берётся из gradle.properties, чтобы у всех платформ она была одна. */
function appVersion(): string {
  try {
    const props = readFileSync(new URL('../gradle.properties', import.meta.url), 'utf8')
    return /pickture\.version=(.+)/.exec(props)?.[1]?.trim() ?? '0.0.0'
  } catch {
    return '0.0.0'
  }
}

const version = appVersion()

function listFiles(dir: string, root = dir): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? listFiles(path, root) : [relative(root, path).replace(/\\/g, '/')]
  })
}

/**
 * Собирает service worker из sw/sw.js: подставляет версию и список файлов оболочки,
 * чтобы приложение открывалось без сети уже после первого запуска.
 */
function serviceWorker(): Plugin {
  return {
    name: 'pickture-sw',
    apply: 'build',
    generateBundle(_options, bundle) {
      const publicFiles = listFiles(fileURLToPath(new URL('./public', import.meta.url)))
        .filter((f) => !f.endsWith('.txt'))
      const files = ['./', ...Object.keys(bundle), ...publicFiles]
        .filter((f) => !f.endsWith('.map'))
        .map((f) => (f === './' ? f : './' + f))
      const template = readFileSync(new URL('./sw/sw.js', import.meta.url), 'utf8')
      const build = `${version}-${Date.now().toString(36)}`
      this.emitFile({
        type: 'asset',
        fileName: 'sw.js',
        source: template
          .replace('__BUILD__', build)
          .replace('__PRECACHE__', JSON.stringify(Array.from(new Set(files)), null, 2)),
      })
    },
  }
}

/**
 * Только для локальной проверки: dev- и preview-сервер отдают ключ TMDB из ../local.properties
 * (как debug-сборка Android). Отвечают только запросам с этого же компьютера; в сборку ключ не попадает.
 */
function devKey(): Plugin {
  const handler = (req: IncomingMessage, res: ServerResponse, next: () => void) => {
    if (!req.url?.endsWith('/__dev/tmdb-key')) return next()
    const remote = req.socket.remoteAddress ?? ''
    if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(remote)) {
      res.statusCode = 403
      return res.end()
    }
    // Переменная PICKTURE_TMDB_KEY — как у версии для ПК; иначе local.properties в корне репозитория
    // (путь можно переопределить через PICKTURE_LOCAL_PROPERTIES, например для git worktree)
    let key = process.env.PICKTURE_TMDB_KEY?.trim() ?? ''
    if (!key) {
      try {
        const path = process.env.PICKTURE_LOCAL_PROPERTIES || fileURLToPath(new URL('../local.properties', import.meta.url))
        key = /^TMDB_API_KEY=(.*)$/m.exec(readFileSync(path, 'utf8'))?.[1]?.trim() ?? ''
      } catch {
        key = ''
      }
    }
    res.statusCode = key ? 200 : 404
    res.setHeader('Cache-Control', 'no-store')
    res.end(key)
  }
  return {
    name: 'pickture-dev-key',
    configureServer(server) {
      server.middlewares.use(handler)
    },
    configurePreviewServer(server) {
      server.middlewares.use(handler)
    },
  }
}

export default defineConfig({
  // На GitHub Pages сайт живёт в подпапке (/Pickture/), локально — в корне.
  // Относительный base подходит для обоих случаев; в workflow он задаётся явно.
  base: process.env.VITE_BASE || './',
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  plugins: [preact(), serviceWorker(), devKey()],
  build: {
    target: 'es2020',
    sourcemap: false,
  },
})

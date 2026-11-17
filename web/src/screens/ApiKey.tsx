import type { ComponentChildren } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import { TmdbLinks, checkKey } from '../core/tmdb'
import { getState, invalidateRecs, setApiKey } from '../data/store'
import { BackButton, MarqueeButton, ScreenHeader, TextAction } from '../ui/components'
import { Icon } from '../ui/icons'

/**
 * Ввод ключа TMDB. Показывается при первом запуске (если ключа нет)
 * и из настроек — чтобы заменить ключ.
 */
export function ApiKeyScreen(props: { onSaved: () => void; onBack?: () => void }) {
  const [key, setKey] = useState(() => getState().settings.tmdbApiKey)
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [offerSaveAnyway, setOfferSaveAnyway] = useState(false)
  const devKey = useDevKey()

  async function save(value: string) {
    await setApiKey(value)
    await invalidateRecs()
    props.onSaved()
  }

  async function check(override?: string) {
    const value = (override ?? key).trim()
    if (!value) {
      setError('Вставьте ключ')
      return
    }
    setChecking(true)
    setError(null)
    setOfferSaveAnyway(false)
    const result = await checkKey(value)
    setChecking(false)
    if (result.kind === 'valid') await save(value)
    else if (result.kind === 'invalid') setError('TMDB не принял этот ключ. Проверьте, что скопировали его целиком.')
    else {
      setError(result.message)
      setOfferSaveAnyway(true)
    }
  }

  async function paste() {
    try {
      const text = await navigator.clipboard.readText()
      if (text) {
        setKey(text.trim())
        setError(null)
      }
    } catch {
      // Safari может не дать прочитать буфер — тогда пользователь вставит ключ сам
    }
  }

  const open = (url: string) => window.open(url, '_blank', 'noopener')

  return (
    <div class="scroll centered" style={{ paddingTop: 'var(--sat)', paddingBottom: 'calc(24px + var(--sab))' }}>
      {props.onBack ? (
        <div style={{ padding: '10px 12px 0' }}>
          <BackButton onClick={props.onBack} />
        </div>
      ) : (
        <div style={{ height: '16px' }} />
      )}
      <ScreenHeader
        title="Ключ TMDB"
        subtitle="Pickture берёт данные о фильмах из TMDB — крупной открытой базы кино. Нужен личный ключ: он бесплатный, а получить его можно за пару минут."
      />
      <div class="ticket" style={{ '--notch': '7px', margin: '16px 16px 0', padding: '16px', borderRadius: '10px' }}>
        <Step n={1} text="Зарегистрируйтесь на TMDB, если ещё нет аккаунта">
          <TextAction
            text="Регистрация на themoviedb.org"
            trailingIcon="openInNew"
            onClick={() => open(TmdbLinks.SIGN_UP)}
            style={{ marginLeft: '-10px' }}
          />
        </Step>
        <div class="perforation" style={{ margin: '10px 0', flex: 'none' }} />
        <Step n={2} text="Откройте страницу API и создайте ключ (бесплатный план Developer)">
          <TextAction
            text="Открыть страницу ключей TMDB"
            trailingIcon="openInNew"
            onClick={() => open(TmdbLinks.API_SETTINGS)}
            style={{ marginLeft: '-10px' }}
          />
        </Step>
        <div class="perforation" style={{ margin: '10px 0', flex: 'none' }} />
        <Step n={3} text="Скопируйте «API Key» или «API Read Access Token» и вставьте ниже" />
      </div>

      <div style={{ padding: '20px 16px 0' }}>
        <label class="t-tiny muted" for="tmdb-key" style={{ display: 'block', marginBottom: '6px' }}>
          Ключ TMDB
        </label>
        <form
          class={`field${error ? ' invalid' : ''}`}
          onSubmit={(e) => {
            e.preventDefault()
            check()
          }}
        >
          <input
            id="tmdb-key"
            value={key}
            onInput={(e) => {
              setKey((e.currentTarget as HTMLInputElement).value)
              setError(null)
              setOfferSaveAnyway(false)
            }}
            placeholder="Например, 1a2b3c…"
            autocomplete="off"
            autocapitalize="off"
            autocorrect="off"
            spellcheck={false}
            enterKeyHint="done"
          />
          <button type="button" class="icon-btn press" aria-label="Вставить из буфера" onClick={paste}>
            <Icon name="paste" size={20} />
          </button>
        </form>
        {error && (
          <div class="t-small error" style={{ marginTop: '6px' }}>
            {error}
          </div>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '4px', padding: '12px 16px 0' }}>
        <MarqueeButton text="Проверить и сохранить" onClick={() => check()} loading={checking} />
        {offerSaveAnyway && <TextAction text="Сохранить без проверки" onClick={() => save(key.trim())} />}
      </div>
      {devKey.current && (
        <div style={{ padding: '8px 6px 0' }}>
          <TextAction text="Использовать ключ из local.properties" onClick={() => check(devKey.current!)} />
        </div>
      )}
      <p class="t-small muted" style={{ padding: '16px 16px 0', margin: '0px' }}>
        Ключ хранится только на этом устройстве и отправляется только в TMDB.
      </p>
    </div>
  )
}

/**
 * Локальная разработка: dev-сервер Vite отдаёт ключ из local.properties (см. vite.config.ts).
 * Ключ не показывается в поле и не попадает в сборку; на GitHub Pages запрос не делается.
 */
function useDevKey() {
  const ref = useRef<string | null>(null)
  const [, force] = useState(0)
  useEffect(() => {
    if (!['localhost', '127.0.0.1'].includes(location.hostname)) return
    fetch('./__dev/tmdb-key', { cache: 'no-store' })
      .then((r) => (r.ok ? r.text() : ''))
      .then((k) => {
        if (k.trim()) {
          ref.current = k.trim()
          force(1)
        }
      })
      .catch(() => {})
  }, [])
  return ref
}

function Step(props: { n: number; text: string; children?: ComponentChildren }) {
  return (
    <div style={{ display: 'flex' }}>
      <span class="t-number gold" style={{ width: '28px', flex: 'none', fontSize: '15px', display: 'inline-block' }}>
        {String(props.n).padStart(2, '0')}
      </span>
      <div>
        <div class="t-body">{props.text}</div>
        {props.children}
      </div>
    </div>
  )
}

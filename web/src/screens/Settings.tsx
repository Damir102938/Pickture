import { useState } from 'preact/hooks'
import { selectableGenres } from '../core/genres'
import { clearAll, setUseOnboarding, useApp } from '../data/store'
import { exportBackup, BackupImportButton } from '../ui/backup'
import { OutlineButton, TextAction, TicketDialog, TopBar } from '../ui/components'
import { push } from '../ui/nav'
import { isStandalone, isIos, updateApp, useUpdateAvailable } from '../pwa'

export function SettingsScreen() {
  const settings = useApp((s) => s.settings)
  const titles = useApp((s) => s.titles)
  const persisted = useApp((s) => s.persisted)
  const [confirmReset, setConfirmReset] = useState(false)
  const updateAvailable = useUpdateAvailable()

  const key = settings.tmdbApiKey
  const genreNames = selectableGenres.filter((g) => settings.favoriteGenres.includes(g.movieId)).map((g) => g.name)
  const onboardingLikes = [...titles.values()].filter((e) => e.fromOnboarding && e.reaction > 0).length
  const standalone = isStandalone()

  let n = 0
  const header = (text: string) => <Header number={++n} text={text} />

  return (
    <>
      <TopBar title="Настройки" />
      <div class="scroll" style={{ paddingTop: '18px' }}>
        {header('Ключ TMDB')}
        <div class="t-body" style={{ padding: '0 16px' }}>
          {key ? `Сохранён ключ ${key.slice(0, 4)}…${key.slice(-4)}` : 'Ключ не задан'}
        </div>
        <div style={{ padding: '10px 16px 0' }}>
          <OutlineButton text="Изменить ключ" onClick={() => push({ name: 'apikey' })} />
        </div>

        <Divider />
        {header('Стартовые предпочтения')}
        <SwitchRow
          title="Учитывать в рекомендациях"
          description="Жанры и фильмы, отмеченные на стартовом экране. Если выключить, рекомендации будут строиться только на ваших лайках и дизлайках."
          checked={settings.useOnboarding}
          onChange={(v) => setUseOnboarding(v)}
        />
        <div class="t-small muted" style={{ padding: '0 16px', whiteSpace: 'pre-line' }}>
          {`Жанры: ${genreNames.length ? genreNames.join(', ') : 'не выбраны'}\nФильмов отмечено: ${onboardingLikes}`}
        </div>
        <div style={{ padding: '10px 16px 0' }}>
          <OutlineButton text="Изменить жанры и фильмы" onClick={() => push({ name: 'onboarding' })} />
        </div>

        <Divider />
        {header('Где хранятся данные')}
        <div class="note ticket t-small muted" style={{ '--notch': '6px' }}>
          Ключ, оценки, списки и последние рекомендации хранятся только на этом устройстве и не пропадают после
          закрытия приложения.
          {isIos() && (
            <>
              <br />
              <br />
              <b>На iPhone</b> у приложения на экране «Домой» своё хранилище, отдельное от Safari: то, что отмечено в
              Safari, сюда не попадёт, и наоборот. Если удалить значок с экрана «Домой», данные удалятся вместе с ним.
            </>
          )}
          {isIos() && !standalone && (
            <>
              <br />
              <br />
              Добавьте Pickture на экран «Домой»: Safari → «Поделиться» → «На экран Домой». Ключ и оценки перенесите
              через резервную копию.
            </>
          )}
          <br />
          <br />
          {persisted === true
            ? 'Браузер обещал не удалять данные при нехватке места.'
            : 'Система может очистить данные при нехватке места — сохраняйте резервную копию.'}
        </div>

        <Divider />
        {header('Резервная копия')}
        <div class="t-small muted" style={{ padding: '0 16px' }}>
          Сохраните оценки и списки в файл — например, в «Файлы», Telegram или на почту. Формат общий для iPhone,
          Android и ПК: копию можно перенести между устройствами.
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', padding: '10px 16px 0' }}>
          <OutlineButton text="Сохранить в файл" icon="share" onClick={exportBackup} />
          <BackupImportButton label="Восстановить" outline />
        </div>

        <Divider />
        {header('Данные')}
        <div style={{ padding: '0 16px' }}>
          <OutlineButton text="Сбросить все оценки и списки" danger onClick={() => setConfirmReset(true)} />
        </div>

        <Divider />
        {header('Версия')}
        <div class="t-small muted" style={{ padding: '0 16px' }}>
          {`Версия ${__APP_VERSION__}. Новые версии скачиваются сами, когда есть интернет; когда версия готова, появится кнопка «Обновить».`}
        </div>
        {updateAvailable && (
          <div style={{ padding: '10px 16px 0' }}>
            <OutlineButton text="Обновить сейчас" icon="refresh" onClick={updateApp} />
          </div>
        )}

        <Divider />
        <div class="t-small muted" style={{ padding: '0 16px 16px' }}>
          Данные о фильмах и сериалах предоставлены TMDB.
          <br />
          This product uses the TMDB API but is not endorsed or certified by TMDB.
        </div>
      </div>

      {confirmReset && (
        <TicketDialog
          title="Сбросить всё?"
          onDismiss={() => setConfirmReset(false)}
          actions={
            <>
              <TextAction text="Отмена" onClick={() => setConfirmReset(false)} />
              <TextAction
                text="Сбросить"
                color="var(--error)"
                onClick={() => {
                  setConfirmReset(false)
                  clearAll()
                }}
              />
            </>
          }
        >
          Удалятся все лайки, дизлайки и список «Хочу посмотреть». Отменить нельзя.
        </TicketDialog>
      )}
    </>
  )
}

function Divider() {
  return <div class="perforation settings-divider" style={{ flex: 'none' }} />
}

function Header(props: { number: number; text: string }) {
  return (
    <div class="settings-header">
      <span class="t-number accent">{String(props.number).padStart(2, '0')}</span>
      <span class="t-section">{props.text}</span>
    </div>
  )
}

function SwitchRow(props: { title: string; description: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div class="switch-row" role="switch" aria-checked={props.checked} onClick={() => props.onChange(!props.checked)}>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: '16px', lineHeight: '22px' }}>{props.title}</div>
        <div class="t-small muted">{props.description}</div>
      </div>
      <div class={`switch${props.checked ? ' on' : ''}`} />
    </div>
  )
}

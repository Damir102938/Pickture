import { useEffect, useState } from 'preact/hooks'
import { entityToTitle } from '../core/entities'
import { genresToTv, selectableGenres } from '../core/genres'
import { type Title, titleKey } from '../core/models'
import { userMessage } from '../core/tmdb'
import { applyOnboarding, completeOnboarding, getState, invalidateRecs, repository } from '../data/store'
import { BackupImportButton } from '../ui/backup'
import {
  ErrorBox,
  LoadingBox,
  MarqueeButton,
  Poster,
  ScreenHeader,
  SeatTag,
  Stamp,
  TextAction,
} from '../ui/components'

type Candidates = { state: 'loading' } | { state: 'error'; message: string } | { state: 'ok'; items: Title[] }

function interleave(movies: Title[], tv: Title[]): Title[] {
  const result: Title[] = []
  let m = 0
  let t = 0
  while (m < movies.length || t < tv.length) {
    for (let i = 0; i < 2 && m < movies.length; i++) result.push(movies[m++])
    if (t < tv.length) result.push(tv[t++])
  }
  return result
}

/**
 * Стартовый экран: любимые жанры и фильмы, чтобы рекомендации появились сразу.
 * [edit] — повторное прохождение из настроек.
 */
export function OnboardingScreen(props: { edit: boolean; onDone: () => void }) {
  const [step, setStep] = useState(0)
  const [genres, setGenres] = useState<Set<number>>(() =>
    props.edit ? new Set(getState().settings.favoriteGenres) : new Set(),
  )
  const [selected, setSelected] = useState<Map<string, Title>>(() => {
    if (!props.edit) return new Map()
    // При повторном прохождении показываем прошлый выбор
    const liked = [...getState().titles.values()].filter((e) => e.fromOnboarding && e.reaction > 0).map(entityToTitle)
    return new Map(liked.map((t) => [titleKey(t), t]))
  })
  const [candidates, setCandidates] = useState<Candidates>({ state: 'loading' })
  const [saving, setSaving] = useState(false)

  async function loadCandidates() {
    setCandidates({ state: 'loading' })
    try {
      let list: Title[]
      if (genres.size === 0) {
        const [a, b] = await Promise.all([repository.trending(1), repository.trending(2)])
        list = [...a, ...b]
      } else {
        const movieGenres = [...genres].join('|')
        const tvGenres = genresToTv(genres)
        const params = { with_genres: movieGenres, 'vote_count.gte': '1500' }
        const [m1, m2, tv] = await Promise.all([
          repository.discover('movie', params),
          repository.discover('movie', params, 2),
          tvGenres.length
            ? repository.discover('tv', { with_genres: tvGenres.join('|'), 'vote_count.gte': '500' })
            : Promise.resolve([]),
        ])
        list = interleave([...m1, ...m2], tv)
      }
      // Ранее выбранные — в начало, чтобы их было видно при редактировании
      const seen = new Set<string>()
      const items = [...selected.values(), ...list].filter((t) => {
        const k = titleKey(t)
        if (seen.has(k)) return false
        seen.add(k)
        return true
      })
      setCandidates({ state: 'ok', items })
    } catch (e) {
      setCandidates({ state: 'error', message: userMessage(e) })
    }
  }

  useEffect(() => {
    if (step === 1) loadCandidates()
  }, [step])

  const toggleGenre = (id: number) =>
    setGenres((g) => {
      const next = new Set(g)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const toggleTitle = (t: Title) =>
    setSelected((s) => {
      const next = new Map(s)
      const k = titleKey(t)
      if (next.has(k)) next.delete(k)
      else next.set(k, t)
      return next
    })

  async function finish() {
    setSaving(true)
    await applyOnboarding([...genres], [...selected.values()])
    await invalidateRecs()
    props.onDone()
  }

  async function skip() {
    // При первом запуске просто отмечаем онбординг пройденным, ничего не сохраняя
    if (!props.edit) await completeOnboarding([])
    props.onDone()
  }

  return (
    <div class="screen" style={{ position: 'absolute' }}>
      <div class="scroll centered" style={{ paddingTop: 'var(--sat)' }}>
        {step === 0 ? (
          <div style={{ paddingBottom: '16px' }}>
            <ScreenHeader
              title="Что вы любите смотреть?"
              kicker="Шаг 1 из 2 · жанры"
              subtitle="Выберите жанры, и рекомендации появятся сразу. Выбор можно изменить или совсем отключить в настройках."
            />
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', padding: '16px 16px 0' }}>
              {selectableGenres.map((g) => (
                <SeatTag
                  key={g.movieId}
                  text={g.name}
                  selected={genres.has(g.movieId)}
                  onClick={() => toggleGenre(g.movieId)}
                />
              ))}
            </div>
            {!props.edit && (
              <div style={{ padding: '20px 6px 0' }}>
                <BackupImportButton
                  label="У меня есть резервная копия — восстановить"
                  onImported={props.onDone}
                />
              </div>
            )}
          </div>
        ) : candidates.state === 'loading' ? (
          <LoadingBox fill />
        ) : candidates.state === 'error' ? (
          <ErrorBox message={candidates.message} onRetry={loadCandidates} />
        ) : (
          <div class="grid" style={{ paddingTop: '0px', gap: '12px 10px' }}>
            <div class="span" style={{ margin: '0 -16px' }}>
              <ScreenHeader
                title="Что из этого понравилось?"
                kicker="Шаг 2 из 2 · фильмы"
                subtitle="Отметьте несколько — чем больше, тем точнее подборка."
              />
            </div>
            {candidates.items.map((t) => {
              const on = selected.has(titleKey(t))
              return (
                <div
                  key={titleKey(t)}
                  class={`selectable press${on ? ' selected' : ''}`}
                  role="checkbox"
                  aria-checked={on}
                  onClick={() => toggleTitle(t)}
                >
                  <Poster path={t.posterPath}>{on && <Stamp text="Моё" color="var(--gold)" />}</Poster>
                  <div class="t-small clamp2" style={{ marginTop: '4px' }}>
                    {t.name}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
      <div class="onboarding-footer centered">
        <div class="perforation" style={{ flex: 'none' }} />
        <div class="row">
          {step === 0 ? (
            <>
              <TextAction text={props.edit ? 'Отмена' : 'Пропустить'} muted onClick={skip} />
              <div style={{ flex: 1 }} />
              <MarqueeButton text="Далее" onClick={() => setStep(1)} />
            </>
          ) : (
            <>
              <TextAction text="Назад" muted onClick={() => setStep(0)} />
              <div style={{ flex: 1 }} />
              <MarqueeButton
                text={selected.size === 0 ? 'Готово' : `Готово · ${selected.size}`}
                onClick={finish}
                loading={saving}
              />
            </>
          )}
        </div>
      </div>
    </div>
  )
}

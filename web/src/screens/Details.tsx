import { useEffect, useState } from 'preact/hooks'
import {
  type MediaType,
  type TitleDetails,
  Images,
  REACTION,
  formatFull,
  formatRating,
  keyOf,
  titleKey,
  today,
  videoTypeLabel,
  yearOf,
} from '../core/models'
import { userMessage } from '../core/tmdb'
import { markOf, repository, setReaction, toggleWatchlist, useApp } from '../data/store'
import {
  BackButton,
  ErrorBox,
  Img,
  LoadingBox,
  MarqueeButton,
  Poster,
  SectionHeader,
  Stamp,
  StampButton,
  TicketField,
  TitleCard,
  matchColor,
} from '../ui/components'
import { Icon } from '../ui/icons'
import { useDesktop } from '../ui/layout'
import { back, push } from '../ui/nav'

type Load = { state: 'loading' } | { state: 'error'; message: string } | { state: 'ok'; data: TitleDetails }

const memory = new Map<string, TitleDetails>()

export function DetailsScreen({ type, id }: { type: MediaType; id: number }) {
  const key = keyOf(type, id)
  const [load, setLoad] = useState<Load>(() => {
    const cached = memory.get(key)
    return cached ? { state: 'ok', data: cached } : { state: 'loading' }
  })
  const profile = useApp((s) => s.profile)
  const mark = useApp((s) => {
    const e = s.titles.get(key)
    return { reaction: e?.reaction ?? 0, inWatchlist: e?.inWatchlist ?? false }
  })

  async function fetchDetails() {
    if (!memory.has(key)) setLoad({ state: 'loading' })
    try {
      const data = await repository.details(type, id)
      memory.set(key, data)
      setLoad({ state: 'ok', data })
    } catch (e) {
      if (!memory.has(key)) setLoad({ state: 'error', message: userMessage(e) })
    }
  }

  useEffect(() => {
    fetchDetails()
  }, [key])

  return (
    <>
      <div class="scroll details-scroll" style={{ paddingTop: '0px' }}>
        {load.state === 'loading' ? (
          <LoadingBox fill />
        ) : load.state === 'error' ? (
          <div style={{ paddingTop: 'calc(60px + var(--sat))' }}>
            <ErrorBox message={load.message} onRetry={fetchDetails} />
          </div>
        ) : (
          <DetailsContent details={load.data} mark={mark} match={profile.matchPercent(load.data.title)} />
        )}
      </div>
      <BackButton onClick={back} onImage class="floating-back" />
    </>
  )
}

function DetailsContent(props: { details: TitleDetails; mark: { reaction: number; inWatchlist: boolean }; match: number | null }) {
  const { details, mark, match } = props
  const t = details.title
  const profile = useApp((s) => s.profile)
  // На ПК картинки крупнее
  const desktop = useDesktop()
  const toggleReaction = (r: number) => setReaction(t, mark.reaction === r ? REACTION.NONE : r)
  const trailer = details.videos[0]
  const more = details.videos.length - 1
  const tagline = details.tagline?.trim().replace(/^[«»"“”„]+|[«»"“”„]+$/g, '').trim()
  const year = yearOf(t.date)

  return (
    <>
      <div class="hero">
        <Img src={Images.backdrop(t.backdropPath, desktop ? 'w1280' : 'w780') ?? Images.poster(t.posterPath, 'w780')} eager />
      </div>
      <div class="details-main">
      <div class="details-head">
        <Poster path={t.posterPath} size={desktop ? 'w500' : 'w342'} eager />
        <div class="info">
          {details.genres.length > 0 && (
            <div class="t-kicker" style={{ letterSpacing: '1px' }}>
              {details.genres.slice(0, 3).join(' · ')}
            </div>
          )}
          <h1 class="details-title" style={{ margin: '0px' }}>
            {t.name}
          </h1>
          {t.originalName && <div class="t-small muted">{t.originalName}</div>}
        </div>
      </div>
      <div class="details-body">
        <div class="chips">
          {year && <TicketField label="Год" value={String(year)} />}
          {t.type === 'tv'
            ? details.seasons !== null && <TicketField label="Сезоны" value={String(details.seasons)} />
            : details.runtimeMinutes !== null && (
                <TicketField
                  label="Хрон."
                  value={`${Math.floor(details.runtimeMinutes / 60)}:${String(details.runtimeMinutes % 60).padStart(2, '0')}`}
                />
              )}
          {t.voteCount > 0 && <TicketField label="TMDB" value={`★ ${formatRating(t.rating)}`} color="var(--gold)" />}
          {match !== null && <Stamp text={`Совпадение ${match}%`} color={matchColor(match)} style={{ marginTop: '10px', marginLeft: '4px' }} />}
        </div>
        <div class="stamp-row">
          <StampButton
            icon={mark.reaction === REACTION.LIKE ? 'favorite' : 'favoriteBorder'}
            label="Нравится"
            selected={mark.reaction === REACTION.LIKE}
            color="var(--accent)"
            onClick={() => toggleReaction(REACTION.LIKE)}
          />
          <StampButton
            icon={mark.reaction === REACTION.DISLIKE ? 'thumbDown' : 'thumbDownOutline'}
            label="Не моё"
            selected={mark.reaction === REACTION.DISLIKE}
            color="var(--match-low)"
            onClick={() => toggleReaction(REACTION.DISLIKE)}
          />
          <StampButton
            icon={mark.inWatchlist ? 'bookmark' : 'bookmarkBorder'}
            label="В список"
            selected={mark.inWatchlist}
            color="var(--gold)"
            onClick={() => toggleWatchlist(t)}
          />
        </div>
        {trailer && (
          <div style={{ marginTop: '12px' }}>
            <MarqueeButton
              block
              icon="play"
              text={more > 0 ? `Смотреть трейлер · ещё ${more}` : `Смотреть ${videoTypeLabel(trailer.type).toLowerCase()}`}
              onClick={() => push({ name: 'trailer', type: t.type, id: t.id, video: trailer.key })}
            />
          </div>
        )}
        <ReleaseInfo details={details} />
        {tagline && (
          <div class="tagline t-body" style={{ marginTop: '16px' }}>
            «{tagline}»
          </div>
        )}
        {t.overview.trim() !== '' && (
          <p class="t-body" style={{ margin: '12px 0 0' }}>
            {t.overview}
          </p>
        )}
        {details.directors.length > 0 && (
          <div class="t-small muted" style={{ marginTop: '10px' }}>
            {(t.type === 'movie' ? 'Режиссёр: ' : 'Создатели: ') + details.directors.join(', ')}
          </div>
        )}
      </div>
      </div>
      {details.cast.length > 0 && (
        <section style={{ paddingTop: '16px' }}>
          <SectionHeader title="В ролях" />
          <div class="row-scroll people">
            {details.cast.map((p) => (
              <div class="person" key={p.id}>
                <div class="poster photo">
                  <Icon name="person" size={24} />
                  <Img src={Images.profile(p.photoPath)} />
                </div>
                <div class="clamp2">{p.name}</div>
                {p.character && <div class="muted ellipsis">{p.character}</div>}
              </div>
            ))}
          </div>
        </section>
      )}
      {details.similar.length > 0 && (
        <section style={{ paddingTop: '16px' }}>
          <SectionHeader title="Похожее" />
          <div class="row-scroll cards">
            {details.similar.map((s) => (
              <TitleCard
                key={titleKey(s)}
                title={s}
                match={profile.matchPercent(s)}
                mark={markOf(titleKey(s))}
                onClick={() => push({ name: 'details', type: s.type, id: s.id })}
                width={112}
              />
            ))}
          </div>
        </section>
      )}
    </>
  )
}

/** Даты выхода — отдельный билетик: слева корешок «РЕЛИЗ», справа строки. */
function ReleaseInfo({ details }: { details: TitleDetails }) {
  const now = today()
  const lines: string[] = []
  if (details.title.type === 'movie') {
    const th = details.theatricalDate
    if (th) lines.push((th > now ? 'Премьера в кино: ' : 'Вышел в кино: ') + formatFull(th))
    const digital = details.digitalDate
    if (digital) lines.push((digital > now ? 'Выйдет онлайн: ' : 'Доступен онлайн с ') + formatFull(digital))
    else if (th && th <= now) lines.push('Дата цифрового релиза пока неизвестна')
  } else {
    if (details.status) lines.push('Статус: ' + statusName(details.status))
    const d = details.title.date
    if (d) lines.push((d > now ? 'Премьера: ' : 'Первый эпизод: ') + formatFull(d))
    const ep = details.nextEpisode
    if (ep) lines.push(`Следующая серия: ${ep.season} сезон, ${ep.number} эпизод` + (ep.date ? ` — ${formatFull(ep.date)}` : ''))
  }
  if (lines.length === 0) return null
  return (
    <div class="release ticket-v" style={{ marginTop: '16px' }}>
      <div class="stub">
        <Icon name="event" size={18} class="gold" />
        <span class="t-tiny muted">Релиз</span>
      </div>
      <div class="perforation-v" />
      <div class="lines">
        {lines.map((l) => (
          <div key={l}>{l}</div>
        ))}
      </div>
    </div>
  )
}

function statusName(status: string) {
  switch (status) {
    case 'Returning Series':
      return 'продолжается'
    case 'Ended':
      return 'завершён'
    case 'Canceled':
      return 'закрыт'
    case 'In Production':
      return 'в производстве'
    case 'Planned':
      return 'запланирован'
    case 'Pilot':
      return 'пилот'
    default:
      return status
  }
}

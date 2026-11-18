import type { ComponentChildren } from 'preact'
import { useMemo } from 'preact/hooks'
import { type UserTitleEntity, diaryDate, entityKey, entityMediaType, entityToTitle } from '../core/entities'
import { RatingScale, today } from '../core/models'
import { Poster, SectionHeader, TicketField } from '../ui/components'
import { push } from '../ui/nav'
import { RatingBadge, ratingColor } from '../ui/rating'

const MONTHS = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']
const MONTHS_GEN = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']

function monthTitle(iso: string): string {
  const [y, m] = iso.split('-').map(Number)
  return `${MONTHS[m - 1]} ${y}`
}

function dayMonth(iso: string): string {
  const [, m, d] = iso.split('-').map(Number)
  return `${d} ${MONTHS_GEN[m - 1]}`
}

/** Дневник: всё, что понравилось или оценено, по месяцам. Дата — день отметки. Сверху — короткая статистика. */
export function DiaryList(props: { watched: UserTitleEntity[]; byRating?: boolean; sortBar?: ComponentChildren }) {
  const { watched, byRating = false } = props
  // По дате — группы по месяцам; по оценке — группы «10 · Шедевр», «9 · Отлично»…, без оценки в конце
  const groups = useMemo(() => {
    const sorted = byRating
      ? [...watched].sort((a, b) => b.userRating - a.userRating || diaryDate(b).localeCompare(diaryDate(a)))
      : [...watched].sort((a, b) => diaryDate(b).localeCompare(diaryDate(a)) || b.updatedAt - a.updatedAt)
    const map = new Map<string, UserTitleEntity[]>()
    for (const e of sorted) {
      const key = byRating
        ? e.userRating > 0
          ? `${e.userRating} · ${RatingScale.label(e.userRating)}`
          : 'Без оценки'
        : monthTitle(diaryDate(e).slice(0, 7) + '-01')
      const list = map.get(key) ?? []
      list.push(e)
      map.set(key, list)
    }
    return [...map.entries()]
  }, [watched, byRating])
  const rated = watched.filter((e) => e.userRating > 0)
  const year = today().slice(0, 4)
  const thisYear = watched.filter((e) => diaryDate(e).startsWith(year)).length
  const average = rated.length ? (rated.reduce((s, e) => s + e.userRating, 0) / rated.length).toFixed(1) : '—'

  return (
    <div class="diary">
      <div class="diary-stats">
        <TicketField label="Всего" value={String(watched.length)} />
        <TicketField label="В этом году" value={String(thisYear)} />
        <TicketField label="Средняя" value={average} color="var(--gold)" />
      </div>
      {props.sortBar}
      {groups.map(([title, items]) => (
        <section key={title}>
          <SectionHeader title={`${title} · ${items.length}`} />
          <div class="diary-list">
            {items.map((e) => (
              <DiaryRow key={entityKey(e)} entity={e} />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

function DiaryRow({ entity }: { entity: UserTitleEntity }) {
  const type = entityMediaType(entity)
  const year = entity.date?.slice(0, 4)
  const meta = [type === 'tv' ? 'Сериал' : 'Фильм', year, `отмечено ${dayMonth(diaryDate(entity))}`]
    .filter(Boolean)
    .join(' · ')
  return (
    <div
      class="diary-row ticket-v press"
      role="link"
      onClick={() => {
        const t = entityToTitle(entity)
        push({ name: 'details', type: t.type, id: t.id })
      }}
    >
      <Poster path={entity.posterPath} size="w185" />
      <div class="body">
        <div class="name clamp2">{entity.name}</div>
        <div class="t-small muted ellipsis">{meta}</div>
        {entity.userRating > 0 && (
          <div class="t-tiny" style={{ color: ratingColor(entity.userRating) }}>
            {RatingScale.label(entity.userRating)}
          </div>
        )}
      </div>
      <div class="perforation-v" />
      <div class="stub">
        {entity.userRating > 0 ? <RatingBadge rating={entity.userRating} large /> : <span class="t-number muted">—</span>}
      </div>
    </div>
  )
}

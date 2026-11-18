import { useMemo } from 'preact/hooks'
import { type UserTitleEntity, entityKey, entityMediaType, entityToTitle } from '../core/entities'
import { RatingScale, today } from '../core/models'
import { Poster, SectionHeader, TicketField } from '../ui/components'
import { push } from '../ui/nav'
import { RatingBadge, ratingColor } from '../ui/rating'

const MONTHS = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']
const MONTHS_GEN = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']

function monthTitle(iso: string | null): string {
  if (!iso) return 'Без даты'
  const [y, m] = iso.split('-').map(Number)
  return `${MONTHS[m - 1]} ${y}`
}

function dayMonth(iso: string): string {
  const [, m, d] = iso.split('-').map(Number)
  return `${d} ${MONTHS_GEN[m - 1]}`
}

/** Дневник: просмотренное по месяцам, с оценками и датами. Сверху — короткая статистика. */
export function DiaryList({ watched }: { watched: UserTitleEntity[] }) {
  const groups = useMemo(() => {
    const sorted = [...watched].sort(
      (a, b) => (b.watchedAt ?? '').localeCompare(a.watchedAt ?? '') || b.updatedAt - a.updatedAt,
    )
    const map = new Map<string, UserTitleEntity[]>()
    for (const e of sorted) {
      const key = e.watchedAt ? e.watchedAt.slice(0, 7) : ''
      const list = map.get(key) ?? []
      list.push(e)
      map.set(key, list)
    }
    return [...map.entries()]
  }, [watched])
  const rated = watched.filter((e) => e.userRating > 0)
  const year = today().slice(0, 4)
  const thisYear = watched.filter((e) => e.watchedAt?.startsWith(year)).length
  const average = rated.length ? (rated.reduce((s, e) => s + e.userRating, 0) / rated.length).toFixed(1) : '—'

  return (
    <div class="diary">
      <div class="diary-stats">
        <TicketField label="Всего" value={String(watched.length)} />
        <TicketField label="В этом году" value={String(thisYear)} />
        <TicketField label="Средняя" value={average} color="var(--gold)" />
      </div>
      {groups.map(([month, items]) => (
        <section key={month || 'none'}>
          <SectionHeader title={`${monthTitle(month ? month + '-01' : null)} · ${items.length}`} />
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
  const meta = [type === 'tv' ? 'Сериал' : 'Фильм', year, entity.watchedAt ? `смотрел ${dayMonth(entity.watchedAt)}` : null]
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

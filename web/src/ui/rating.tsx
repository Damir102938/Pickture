import { useRef, useState } from 'preact/hooks'
import { type UserMark, RatingScale, formatFull, today } from '../core/models'
import { MarqueeButton, TextAction, TicketDialog } from './components'
import { Icon } from './icons'

/** Цвет оценки: 7+ — зелёный, 5–6 — янтарный, ниже — приглушённый (как в Android). */
export function ratingColor(rating: number): string {
  if (rating >= 7) return 'var(--match-high)'
  if (rating >= 5) return 'var(--match-mid)'
  return 'var(--match-low)'
}

/** Маленький значок оценки на постере и в дневнике: «8». */
export function RatingBadge({ rating, large = false }: { rating: number; large?: boolean }) {
  return (
    <div
      class={`rating-badge${large ? ' large' : ''}`}
      style={{ color: ratingColor(rating), borderColor: ratingColor(rating) }}
      aria-label={`Оценка ${rating}`}
    >
      {rating}
    </div>
  )
}

/**
 * Выбор оценки 1..10 — два ряда «мест в зале». Повторное нажатие снимает оценку.
 * Под рядами — словесная расшифровка («Отлично»).
 */
export function RatingPicker(props: { selected: number; onSelect: (value: number) => void }) {
  const { selected } = props
  return (
    <div class="rating-picker" role="radiogroup" aria-label="Оценка от 1 до 10">
      {[
        [1, 2, 3, 4, 5],
        [6, 7, 8, 9, 10],
      ].map((row) => (
        <div class="rating-row" key={row[0]}>
          {row.map((value) => {
            const color = ratingColor(selected > 0 ? selected : value)
            const active = selected > 0 && value <= selected
            const current = value === selected
            return (
              <button
                key={value}
                role="radio"
                aria-checked={current}
                aria-label={`Оценка ${value}`}
                class={`rating-seat press${active ? ' active' : ''}${current ? ' current' : ''}`}
                style={{ '--seat': color }}
                onClick={() => props.onSelect(current ? 0 : value)}
              >
                {value}
              </button>
            )
          })}
        </div>
      ))}
      <div class="t-label rating-caption" style={{ color: selected > 0 ? ratingColor(selected) : 'var(--muted)' }}>
        {selected > 0 ? `${selected} из 10 · ${RatingScale.label(selected)}` : 'Без оценки — можно поставить позже'}
      </div>
    </div>
  )
}

/** Поле даты в стиле билета: по нажатию открывается системный календарь, будущие даты выбрать нельзя. */
function DateField(props: { value: string; onChange: (value: string) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const now = today()
  const open = () => {
    const el = input.current
    if (!el) return
    try {
      el.showPicker()
    } catch {
      el.focus()
      el.click()
    }
  }
  return (
    <div class="date-field press" onClick={open} role="button" aria-label="Дата просмотра">
      <Icon name="event" size={18} class="gold" />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div class="t-tiny muted">Дата просмотра</div>
        <div class="t-body">{props.value === now ? 'Сегодня' : formatFull(props.value)}</div>
      </div>
      <span class="t-tiny accent">Изменить</span>
      <input
        ref={input}
        type="date"
        value={props.value}
        max={now}
        onChange={(e) => {
          const v = (e.currentTarget as HTMLInputElement).value
          if (v && v <= now) props.onChange(v)
        }}
        tabIndex={-1}
        aria-hidden="true"
      />
    </div>
  )
}

/** «Посмотрел»: оценка 1–10 и дата просмотра. Если уже смотрел — можно убрать отметку. */
export function WatchedDialog(props: {
  mark: UserMark
  isSeries: boolean
  onSave: (rating: number, date: string) => void
  onClear: () => void
  onDismiss: () => void
}) {
  const [rating, setRating] = useState(props.mark.rating)
  const [date, setDate] = useState(props.mark.watchedAt ?? today())
  const title = props.mark.watched ? 'Ваша оценка' : props.isSeries ? 'Посмотрел сериал' : 'Посмотрел фильм'
  return (
    <TicketDialog
      title={title}
      onDismiss={props.onDismiss}
      actions={
        <>
          {props.mark.watched ? (
            <TextAction text="Убрать" muted onClick={props.onClear} />
          ) : (
            <TextAction text="Отмена" muted onClick={props.onDismiss} />
          )}
          <MarqueeButton text="Сохранить" onClick={() => props.onSave(rating, date)} />
        </>
      }
    >
      <RatingPicker selected={rating} onSelect={setRating} />
      <DateField value={date} onChange={setDate} />
      <div class="t-small muted" style={{ marginTop: '10px' }}>
        Оценка 9–10 сильнее всего влияет на рекомендации, 1–4 — отталкивает от похожего.
      </div>
    </TicketDialog>
  )
}

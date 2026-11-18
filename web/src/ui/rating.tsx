import { type UserMark, REACTION, RatingScale } from '../core/models'

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
 * Уточнение оценки: один ряд «мест в зале» (после лайка 6–10, после «Не моё» 1–5).
 * Повторное нажатие на выбранное место снимает уточнение.
 */
export function RatingRow(props: { range: number[]; selected: number; onSelect: (value: number) => void }) {
  const { range, selected } = props
  const inRange = range.includes(selected)
  return (
    <div class="rating-row" role="radiogroup" aria-label="Уточнить оценку">
      {range.map((value) => {
        const color = ratingColor(selected > 0 ? selected : value)
        const active = inRange && value <= selected
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
  )
}

/**
 * Полоса уточнения под кнопками: появляется после лайка или «Не моё».
 * Оценку можно не трогать — отметка в одно касание уже сохранена. Дата в дневнике ставится сама.
 */
export function RefineStrip(props: { mark: UserMark; onRate: (value: number) => void }) {
  const { mark } = props
  const liked = mark.reaction === REACTION.LIKE
  const caption = mark.rating > 0 ? `${mark.rating} из 10 · ${RatingScale.label(mark.rating)}` : liked ? 'Просто нравится' : 'Не интересно'
  return (
    <div class="refine-strip">
      <div class="refine-head">
        <span class="t-tiny muted">Уточнить оценку</span>
        <span class="t-tiny" style={{ color: mark.rating > 0 ? ratingColor(mark.rating) : 'var(--muted)' }}>
          {caption}
        </span>
      </div>
      <RatingRow range={RatingScale.range(mark.reaction)} selected={mark.rating} onSelect={props.onRate} />
      {!liked && mark.rating === 0 && (
        <div class="t-small muted" style={{ marginTop: '6px' }}>
          Не смотрели — просто не будем предлагать похожее. Смотрели — поставьте оценку.
        </div>
      )}
    </div>
  )
}

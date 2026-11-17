import { Fragment, type ComponentChildren, type CSSProperties } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import {
  type Title,
  type TitleKey,
  type UserMark,
  Images,
  REACTION,
  formatRating,
  titleKey,
  titleSubtitle,
} from '../core/models'
import { Icon, type IconName } from './icons'
import { back } from './nav'

export const matchColor = (p: number) => (p >= 75 ? 'var(--match-high)' : p >= 55 ? 'var(--match-mid)' : 'var(--match-low)')

const pad2 = (n: number) => String(n).padStart(2, '0')

// ---------- Шапки ----------

export function ScreenHeader(props: {
  title: string
  kicker?: string
  subtitle?: string
  trailing?: ComponentChildren
}) {
  return (
    <div class="screen-header">
      <div class="grow">
        {props.kicker && <div class="t-kicker">{props.kicker}</div>}
        <h1 class="t-screen" style={{ margin: 0 }}>
          {props.title}
        </h1>
        {props.subtitle && <div class="subtitle">{props.subtitle}</div>}
      </div>
      {props.trailing}
    </div>
  )
}

export function SectionHeader(props: { title: string; number?: number; subtitle?: string; onAll?: () => void; inset?: boolean }) {
  return (
    <div class={`section-header${props.onAll ? ' with-all' : ''}`} style={props.inset === false ? { padding: 0 } : undefined}>
      <div class="section-header-row">
        {props.number !== undefined && <span class="t-number num">{pad2(props.number)}</span>}
        <h2 class="t-section" style={{ margin: '0 10px 0 0' }}>
          {props.title}
        </h2>
        <div class="perforation" />
        {props.onAll && <TextAction text="Все →" onClick={props.onAll} />}
      </div>
      {props.subtitle && <div class={`subtitle${props.number !== undefined ? ' indented' : ''}`}>{props.subtitle}</div>}
    </div>
  )
}

/** Шапка вложенного экрана: жетон «назад», название и пунктир снизу. */
export function TopBar(props: { title: string; onBack?: () => void; actions?: ComponentChildren }) {
  return (
    <div class="top-bar">
      <div class="top-bar-row">
        <BackButton onClick={props.onBack ?? back} />
        <div class="t-bar ellipsis">{props.title}</div>
        {props.actions}
      </div>
      <div class="perforation" />
    </div>
  )
}

// ---------- Кнопки ----------

export function MarqueeButton(props: {
  text: string
  onClick: () => void
  icon?: IconName
  disabled?: boolean
  loading?: boolean
  block?: boolean
}) {
  return (
    <button
      class={`marquee-btn ticket press${props.block ? ' block' : ''}`}
      disabled={props.disabled || props.loading}
      onClick={props.onClick}
    >
      {props.loading ? (
        <MarqueeLoader dot={5} />
      ) : (
        props.icon && <Icon name={props.icon} size={18} />
      )}
      <span class="t-label ellipsis">{props.text}</span>
    </button>
  )
}

export function OutlineButton(props: {
  text: string
  onClick: () => void
  icon?: IconName
  disabled?: boolean
  danger?: boolean
}) {
  return (
    <button class={`outline-btn press${props.danger ? ' danger' : ''}`} disabled={props.disabled} onClick={props.onClick}>
      {props.icon && <Icon name={props.icon} size={16} />}
      <span class="t-label ellipsis">{props.text}</span>
    </button>
  )
}

export function TextAction(props: {
  text: string
  onClick: () => void
  muted?: boolean
  disabled?: boolean
  trailingIcon?: IconName
  color?: string
  style?: CSSProperties
}) {
  return (
    <button
      class={`text-action press${props.muted ? ' muted' : ''}`}
      disabled={props.disabled}
      onClick={props.onClick}
      style={{ ...(props.color ? { color: props.color } : {}), ...props.style }}
    >
      <span class="t-label">{props.text}</span>
      {props.trailingIcon && <Icon name={props.trailingIcon} size={15} />}
    </button>
  )
}

export function BackButton(props: { onClick: () => void; onImage?: boolean; class?: string }) {
  return (
    <button
      class={`back-btn press${props.onImage ? ' on-image' : ''}${props.class ? ' ' + props.class : ''}`}
      onClick={props.onClick}
      aria-label="Назад"
    >
      <Icon name="back" size={24} />
    </button>
  )
}

/** Переключатель вариантов — полоса билета, выбранная часть «прокомпостирована» золотом. */
export function TicketToggle(props: {
  options: string[]
  selected: number
  onSelect: (i: number) => void
  icons?: IconName[]
  descriptions?: string[]
  style?: CSSProperties
}) {
  return (
    <div class="toggle" role="tablist" style={props.style}>
      {props.options.map((label, i) => (
        <Fragment key={i}>
          {i > 0 && <div class="perforation-v" />}
          <button
            role="tab"
            aria-selected={i === props.selected}
            aria-label={props.descriptions?.[i]}
            class={`toggle-item press${i === props.selected ? ' active' : ''}`}
            onClick={() => props.onSelect(i)}
          >
            {props.icons?.[i] && <Icon name={props.icons[i]} size={16} />}
            {label && <span class="t-label ellipsis">{label}</span>}
          </button>
        </Fragment>
      ))}
    </div>
  )
}

/** Метка-«место в зале» для жанров и сортировки. */
export function SeatTag(props: { text: string; selected: boolean; onClick: () => void }) {
  return (
    <button
      class={`seat press${props.selected ? ' selected' : ''}`}
      role="checkbox"
      aria-checked={props.selected}
      onClick={props.onClick}
    >
      {props.selected && <Icon name="check" size={14} />}
      {props.text}
    </button>
  )
}

/** Наклонная печать: «СОВПАДЕНИЕ 90%». */
export function Stamp(props: { text: string; color: string; style?: CSSProperties }) {
  return (
    <span class="stamp t-label" style={{ color: props.color, ...props.style }}>
      {props.text}
    </span>
  )
}

/** Кнопка оценки в карточке — отрывной талон с подписью. */
export function StampButton(props: { icon: IconName; label: string; selected: boolean; color: string; onClick: () => void }) {
  return (
    <button
      class={`stamp-btn press${props.selected ? ' selected' : ''}`}
      style={{ '--sel': props.color } as CSSProperties}
      role="checkbox"
      aria-checked={props.selected}
      onClick={props.onClick}
    >
      <Icon name={props.icon} size={20} key={String(props.selected)} />
      <span class="t-tiny ellipsis">{props.label}</span>
    </button>
  )
}

export function StampIconButton(props: { icon: IconName; label: string; onClick: () => void; color?: string }) {
  return (
    <button
      class="stamp-icon-btn press"
      aria-label={props.label}
      style={{ color: props.color ?? 'var(--text)' }}
      onClick={(e) => {
        e.stopPropagation()
        props.onClick()
      }}
    >
      <Icon name={props.icon} size={18} />
    </button>
  )
}

/** Поля билета: «ГОД 2014», «ХРОН. 2:49». */
export function TicketField(props: { label: string; value: string; color?: string }) {
  return (
    <div class="ticket-field">
      <div class="t-tiny muted">{props.label}</div>
      <div class="value" style={props.color ? { color: props.color } : undefined}>
        {props.value}
      </div>
    </div>
  )
}

// ---------- Загрузка и сообщения ----------

/** Загрузка — бегущие огоньки, как на вывеске кинотеатра. */
export function MarqueeLoader({ dot = 8 }: { dot?: number }) {
  return (
    <span class="marquee-loader" style={{ '--dot': `${dot}px` } as CSSProperties} role="progressbar" aria-label="Загрузка">
      <span />
      <span />
      <span />
      <span />
      <span />
    </span>
  )
}

export function LoadingBox({ fill = false }: { fill?: boolean }) {
  return (
    <div class={`loading-box${fill ? ' fill' : ''}`}>
      <MarqueeLoader />
    </div>
  )
}

export function MessageBox(props: { title: string; text?: string; actionLabel?: string; onAction?: () => void }) {
  return (
    <div class="message-box">
      <div class="t-section">{props.title}</div>
      <div class="perforation gold" />
      {props.text && <div class="t-body muted">{props.text}</div>}
      {props.actionLabel && props.onAction && <MarqueeButton text={props.actionLabel} onClick={props.onAction} />}
    </div>
  )
}

export const ErrorBox = (props: { message: string; onRetry: () => void }) => (
  <MessageBox title="Не удалось загрузить" text={props.message} actionLabel="Повторить" onAction={props.onRetry} />
)

// ---------- Постеры и карточки ----------

export function Img(props: { src: string | null; alt?: string; class?: string; eager?: boolean }) {
  // Помним, какая картинка загрузилась: из кэша onLoad может прийти раньше эффектов
  const [loadedSrc, setLoadedSrc] = useState<string | null>(null)
  const src = props.src
  if (!src) return null
  return (
    <img
      key={src}
      src={src}
      alt={props.alt ?? ''}
      loading={props.eager ? 'eager' : 'lazy'}
      decoding="async"
      class={`${props.class ?? ''}${loadedSrc === src ? ' loaded' : ''}`}
      ref={(el) => {
        if (el?.complete && el.naturalWidth > 0 && loadedSrc !== src) setLoadedSrc(src)
      }}
      onLoad={() => setLoadedSrc(src)}
      onError={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
    />
  )
}

export function Poster(props: {
  path: string | null
  size?: string
  ratio?: boolean
  class?: string
  style?: CSSProperties
  children?: ComponentChildren
  eager?: boolean
}) {
  return (
    <div class={`poster${props.ratio === false ? '' : ' ratio'}${props.class ? ' ' + props.class : ''}`} style={props.style}>
      <Icon name="movie" size={32} />
      <Img src={Images.poster(props.path, props.size)} eager={props.eager} />
      {props.children}
    </div>
  )
}

/** Отметка пользователя на постере — маленький штамп в углу. */
export function MarkIcon({ mark }: { mark?: UserMark }) {
  if (!mark) return null
  let icon: IconName
  let color: string
  if (mark.reaction === REACTION.LIKE) [icon, color] = ['favorite', 'var(--accent)']
  else if (mark.reaction === REACTION.DISLIKE) [icon, color] = ['thumbDown', 'var(--match-low)']
  else if (mark.inWatchlist) [icon, color] = ['bookmark', 'var(--gold)']
  else return null
  return (
    <div class="mark-icon" style={{ color }}>
      <Icon name={icon} size={14} />
    </div>
  )
}

/** Карточка-билет: постер, линия отрыва и корешок с процентом совпадения и рейтингом TMDB. */
export function TitleCard(props: {
  title: Title
  match: number | null
  mark?: UserMark
  onClick: () => void
  width?: number
}) {
  const t = props.title
  return (
    <div
      class="title-card press"
      style={props.width ? { width: `${props.width}px` } : undefined}
      onClick={props.onClick}
      role="link"
    >
      <div class="ticket">
        <Poster path={t.posterPath}>
          <MarkIcon mark={props.mark} />
        </Poster>
        <div class="perforation" />
        <div class="stub">
          {props.match !== null ? (
            <span class="t-number" style={{ color: matchColor(props.match) }}>
              {props.match}%
            </span>
          ) : (
            <span class="t-tiny muted">{t.type === 'tv' ? 'Сериал' : 'Фильм'}</span>
          )}
          {t.voteCount >= 5 && t.rating > 0 && <span class="t-number gold">★ {formatRating(t.rating)}</span>}
        </div>
      </div>
      <div class="caption">
        <div class="name clamp2">{t.name}</div>
        <div class="t-small muted ellipsis">{titleSubtitle(t)}</div>
      </div>
    </div>
  )
}

/** Сетка карточек с подгрузкой следующей страницы при прокрутке до конца. */
export function TitleGrid(props: {
  items: Title[]
  matchOf: (t: Title) => number | null
  markOf: (key: TitleKey) => UserMark | undefined
  onOpen: (t: Title) => void
  header?: ComponentChildren
  loadingMore?: boolean
  canLoadMore?: boolean
  onLoadMore?: () => void
}) {
  const sentinel = useRef<HTMLDivElement>(null)
  const loadMore = useRef(props.onLoadMore)
  loadMore.current = props.onLoadMore
  useEffect(() => {
    const el = sentinel.current
    if (!el || !props.canLoadMore) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) loadMore.current?.()
      },
      { rootMargin: '600px 0px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [props.canLoadMore, props.items.length])
  return (
    <div class="grid">
      {props.header && <div class="span">{props.header}</div>}
      {props.items.map((t) => (
        <TitleCard
          key={titleKey(t)}
          title={t}
          match={props.matchOf(t)}
          mark={props.markOf(titleKey(t))}
          onClick={() => props.onOpen(t)}
        />
      ))}
      <div class="span" ref={sentinel} style={{ minHeight: 1 }}>
        {props.loadingMore && (
          <div class="loading-box" style={{ minHeight: 60 }}>
            <MarqueeLoader />
          </div>
        )}
      </div>
    </div>
  )
}

// ---------- Диалог и всплывающие сообщения ----------

export function TicketDialog(props: {
  title: string
  children: ComponentChildren
  onDismiss: () => void
  actions: ComponentChildren
}) {
  return (
    <div class="dialog-backdrop" onClick={props.onDismiss}>
      <div class="dialog ticket" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <div class="t-bar">{props.title}</div>
        <div class="perforation" style={{ margin: '14px 0', flex: 'none' }} />
        <div class="t-body">{props.children}</div>
        <div class="actions">{props.actions}</div>
      </div>
    </div>
  )
}

let toastListener: ((text: string | null) => void) | null = null
let toastTimer: ReturnType<typeof setTimeout> | undefined

export function toast(text: string) {
  clearTimeout(toastTimer)
  toastListener?.(text)
  toastTimer = setTimeout(() => toastListener?.(null), 3200)
}

export function ToastHost() {
  const [text, setText] = useState<string | null>(null)
  useEffect(() => {
    toastListener = setText
    return () => {
      toastListener = null
    }
  }, [])
  return text ? (
    <div class="toast" role="status" key={text}>
      {text}
    </div>
  ) : null
}

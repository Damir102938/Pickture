import { useEffect, useRef, useState } from 'preact/hooks'
import { type Title, titleKey } from '../core/models'
import { userMessage } from '../core/tmdb'
import { markOf, repository, useApp } from '../data/store'
import { ErrorBox, LoadingBox, MessageBox, ScreenHeader, TitleGrid } from '../ui/components'
import { Icon } from '../ui/icons'
import { push } from '../ui/nav'
import { useScrollToTop } from './shared'

interface SearchState {
  items: Title[]
  page: number
  hasMore: boolean
  loading: boolean
  error: string | null
  /** Результаты относятся к поиску (а не к трендам, которые показываются при пустом запросе). */
  isSearch: boolean
}

export function SearchScreen() {
  const profile = useApp((s) => s.profile)
  useApp((s) => s.titles)
  const [query, setQuery] = useState('')
  const [state, setState] = useState<SearchState>({
    items: [],
    page: 0,
    hasMore: false,
    loading: true,
    error: null,
    isSearch: false,
  })
  const request = useRef(0)
  const scroll = useRef<HTMLDivElement>(null)
  useScrollToTop(scroll, 'search')

  async function load(q: string, page: number) {
    const id = ++request.current
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const result = q
        ? await repository.search(q, page)
        : { items: await repository.trending(page), page, hasMore: page < 5 }
      if (id !== request.current) return
      setState((s) => {
        const items = page === 1 ? result.items : [...s.items, ...result.items]
        const seen = new Set<string>()
        return {
          items: items.filter((t) => !seen.has(titleKey(t)) && !!seen.add(titleKey(t))),
          page: result.page,
          hasMore: result.hasMore,
          loading: false,
          error: null,
          isSearch: q !== '',
        }
      })
    } catch (e) {
      if (id !== request.current) return
      setState((s) => ({ ...s, loading: false, error: userMessage(e) }))
    }
  }

  // Запрос уходит через 400 мс после того, как пользователь перестал печатать
  useEffect(() => {
    const timer = setTimeout(() => {
      load(query.trim(), 1)
      scroll.current?.scrollTo({ top: 0 })
    }, query ? 400 : 0)
    return () => clearTimeout(timer)
  }, [query.trim()])

  const loadMore = () => {
    if (!state.loading && state.hasMore) load(query.trim(), state.page + 1)
  }

  return (
    <div class="scroll" ref={scroll}>
      <ScreenHeader title="Поиск" />
      <form
        class="field"
        style={{ margin: '8px 16px 0' }}
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          ;(document.activeElement as HTMLElement | null)?.blur()
        }}
      >
        <Icon name="search" size={20} class="gold" />
        <input
          type="search"
          value={query}
          placeholder="Фильм или сериал"
          onInput={(e) => setQuery((e.currentTarget as HTMLInputElement).value)}
          enterKeyHint="search"
          autocomplete="off"
          autocorrect="off"
        />
        {query && (
          <button type="button" class="icon-btn press" aria-label="Очистить" onClick={() => setQuery('')}>
            <Icon name="close" size={20} />
          </button>
        )}
      </form>
      {state.items.length === 0 && state.error ? (
        <ErrorBox message={state.error} onRetry={() => load(query.trim(), 1)} />
      ) : state.items.length === 0 && state.loading ? (
        <LoadingBox fill />
      ) : state.items.length === 0 && state.isSearch ? (
        <MessageBox title="Ничего не нашлось" text="Попробуйте другое название" />
      ) : (
        <TitleGrid
          items={state.items}
          matchOf={(t) => profile.matchPercent(t)}
          markOf={markOf}
          onOpen={(t) => push({ name: 'details', type: t.type, id: t.id })}
          loadingMore={state.loading && state.items.length > 0}
          canLoadMore={state.hasMore && !state.error}
          onLoadMore={loadMore}
          header={
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span class="t-section">{state.isSearch ? 'Результаты поиска' : 'Популярное на этой неделе'}</span>
              <div class="perforation" />
            </div>
          }
        />
      )}
    </div>
  )
}

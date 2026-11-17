import { useEffect, useMemo, useState } from 'preact/hooks'
import type { Title } from '../core/models'
import { type FeedSection, sectionInfo } from '../core/repository'
import { userMessage } from '../core/tmdb'
import { markOf, repository, useApp } from '../data/store'
import { ErrorBox, LoadingBox, SeatTag, TitleGrid, TopBar } from '../ui/components'
import { push } from '../ui/nav'

type SortMode = 'POPULAR' | 'MATCH' | 'DATE'
const SORT_LABELS: Record<SortMode, string> = { POPULAR: 'Популярные', MATCH: 'По вкусу', DATE: 'По дате' }

/** Раздел ленты целиком: сетка с подгрузкой и сортировкой. */
export function SectionScreen({ section }: { section: FeedSection }) {
  const profile = useApp((s) => s.profile)
  useApp((s) => s.titles)
  const [items, setItems] = useState<Title[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sort, setSort] = useState<SortMode>('POPULAR')

  async function loadMore() {
    if (loading || !hasMore) return
    setLoading(true)
    setError(null)
    try {
      const result = await repository.feed(section, page + 1)
      setItems((list) => {
        const seen = new Set(list.map((t) => `${t.type}:${t.id}`))
        return [...list, ...result.items.filter((t) => !seen.has(`${t.type}:${t.id}`))]
      })
      setPage(result.page)
      setHasMore(result.hasMore)
    } catch (e) {
      setError(userMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadMore()
  }, [])

  const sorted = useMemo(() => {
    if (sort === 'MATCH') return [...items].sort((a, b) => (profile.matchPercent(b) ?? 0) - (profile.matchPercent(a) ?? 0))
    if (sort === 'DATE') {
      const upcoming = section === 'UPCOMING_MOVIES' || section === 'UPCOMING_SERIES'
      const d = (t: Title) => t.date ?? ''
      return [...items].sort((a, b) => (upcoming ? d(a).localeCompare(d(b)) : d(b).localeCompare(d(a))))
    }
    return items
  }, [items, sort, profile])

  return (
    <>
      <TopBar title={sectionInfo(section).title} />
      <div class="scroll">
        {items.length === 0 && error ? (
          <ErrorBox message={error} onRetry={loadMore} />
        ) : items.length === 0 ? (
          <LoadingBox fill />
        ) : (
          <TitleGrid
            items={sorted}
            matchOf={(t) => profile.matchPercent(t)}
            markOf={markOf}
            onOpen={(t) => push({ name: 'details', type: t.type, id: t.id })}
            loadingMore={loading}
            canLoadMore={hasMore && !error}
            onLoadMore={loadMore}
            header={
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {(Object.keys(SORT_LABELS) as SortMode[])
                  .filter((m) => m !== 'MATCH' || !profile.isEmpty)
                  .map((m) => (
                    <SeatTag key={m} text={SORT_LABELS[m]} selected={sort === m} onClick={() => setSort(m)} />
                  ))}
              </div>
            }
          />
        )}
      </div>
    </>
  )
}

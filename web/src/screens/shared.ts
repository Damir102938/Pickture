import type { RefObject } from 'preact'
import { useEffect, useRef } from 'preact/hooks'
import { type Tab, useNav } from '../ui/nav'

/** Повторное нажатие на открытую вкладку прокручивает экран наверх — как в iOS. */
export function useScrollToTop(ref: RefObject<HTMLElement | null>, tab: Tab) {
  const nav = useNav()
  const last = useRef(nav.reselect)
  useEffect(() => {
    if (nav.reselect !== last.current && nav.tab === tab) ref.current?.scrollTo({ top: 0, behavior: 'smooth' })
    last.current = nav.reselect
  }, [nav.reselect])
}

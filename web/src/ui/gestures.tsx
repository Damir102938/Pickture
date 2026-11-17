import type { RefObject } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import { MarqueeLoader } from './components'
import { backAfterSwipe } from './nav'

const PULL_TRIGGER = 64

/**
 * «Потяните вниз, чтобы обновить». На iPhone контент сам пружинит у верхнего края,
 * поверх этого показываются огоньки загрузки.
 */
export function usePullToRefresh(scrollRef: RefObject<HTMLElement | null>, onRefresh: () => void, refreshing: boolean) {
  const [pull, setPull] = useState(0)
  const startY = useRef<number | null>(null)
  const pullRef = useRef(0)
  const refreshRef = useRef(onRefresh)
  refreshRef.current = onRefresh

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const onStart = (e: TouchEvent) => {
      startY.current = el.scrollTop <= 0 && e.touches.length === 1 ? e.touches[0].clientY : null
    }
    const onMove = (e: TouchEvent) => {
      if (startY.current === null) return
      const dy = e.touches[0].clientY - startY.current
      if (dy <= 0 || el.scrollTop > 0) {
        if (pullRef.current) setPull((pullRef.current = 0))
        return
      }
      const value = Math.min(dy * 0.5, 96)
      pullRef.current = value
      setPull(value)
    }
    const onEnd = () => {
      if (pullRef.current >= PULL_TRIGGER) {
        navigator.vibrate?.(10)
        refreshRef.current()
      }
      startY.current = null
      pullRef.current = 0
      setPull(0)
    }
    el.addEventListener('touchstart', onStart, { passive: true })
    el.addEventListener('touchmove', onMove, { passive: true })
    el.addEventListener('touchend', onEnd)
    el.addEventListener('touchcancel', onEnd)
    return () => {
      el.removeEventListener('touchstart', onStart)
      el.removeEventListener('touchmove', onMove)
      el.removeEventListener('touchend', onEnd)
      el.removeEventListener('touchcancel', onEnd)
    }
  }, [])

  const visible = refreshing || pull > 8
  return (
    <>
      <div class="ptr" aria-hidden={!refreshing}>
        <div
          style={{
            opacity: refreshing ? 1 : Math.min(pull / PULL_TRIGGER, 1),
            transform: `translateY(${refreshing ? 0 : Math.max(pull - 30, 0)}px)`,
          }}
        >
          {visible && <MarqueeLoader dot={7} />}
        </div>
      </div>
      {refreshing && <div style={{ height: '28px' }} />}
    </>
  )
}

/**
 * Жест «назад» от левого края экрана, как в iOS: экран тянется за пальцем,
 * нижний выезжает навстречу.
 */
export function useSwipeBack(ref: RefObject<HTMLElement | null>, enabled: boolean) {
  useEffect(() => {
    const el = ref.current
    if (!el || !enabled) return
    let startX = 0
    let startY = 0
    let startT = 0
    let tracking = false
    let dragging = false
    let dx = 0
    let under: HTMLElement | null = null

    const reset = () => {
      el.classList.remove('dragging')
      el.style.transform = ''
      if (under) {
        under.classList.remove('dragging')
        under.style.transform = ''
        under.style.filter = ''
      }
    }

    const onStart = (e: TouchEvent) => {
      const t = e.touches[0]
      tracking = e.touches.length === 1 && t.clientX < 28
      dragging = false
      dx = 0
      startX = t.clientX
      startY = t.clientY
      startT = performance.now()
    }
    const onMove = (e: TouchEvent) => {
      if (!tracking) return
      const t = e.touches[0]
      const mx = t.clientX - startX
      const my = t.clientY - startY
      if (!dragging) {
        if (Math.abs(my) > 10 && Math.abs(my) > Math.abs(mx)) {
          tracking = false
          return
        }
        if (mx < 10) return
        dragging = true
        under = el.previousElementSibling as HTMLElement | null
        el.classList.add('dragging')
        under?.classList.add('dragging')
      }
      e.preventDefault()
      dx = Math.max(0, mx)
      const width = el.clientWidth
      const p = Math.min(dx / width, 1)
      el.style.transform = `translateX(${dx}px)`
      if (under) {
        under.style.transform = `translateX(${-28 * (1 - p)}%)`
        under.style.filter = `brightness(${0.6 + 0.4 * p})`
      }
    }
    const onEnd = () => {
      if (!tracking || !dragging) {
        tracking = false
        return
      }
      tracking = false
      dragging = false
      const width = el.clientWidth
      const velocity = dx / Math.max(performance.now() - startT, 1)
      const complete = dx > width * 0.35 || (velocity > 0.5 && dx > 40)
      el.classList.remove('dragging')
      under?.classList.remove('dragging')
      if (complete) {
        el.style.transform = 'translateX(100%)'
        if (under) {
          under.style.transform = 'translateX(0)'
          under.style.filter = 'brightness(1)'
        }
        const target = under
        setTimeout(() => {
          backAfterSwipe()
          // Встроенные стили снимаем, когда нижний экран уже стал верхним (popstate приходит асинхронно)
          setTimeout(() => {
            if (target) {
              target.style.transform = ''
              target.style.filter = ''
            }
          }, 150)
        }, 280)
      } else {
        reset()
      }
    }
    el.addEventListener('touchstart', onStart, { passive: true })
    el.addEventListener('touchmove', onMove, { passive: false })
    el.addEventListener('touchend', onEnd)
    el.addEventListener('touchcancel', onEnd)
    return () => {
      el.removeEventListener('touchstart', onStart)
      el.removeEventListener('touchmove', onMove)
      el.removeEventListener('touchend', onEnd)
      el.removeEventListener('touchcancel', onEnd)
    }
  }, [enabled])
}

import { useEffect, useState } from 'preact/hooks'
import { type MediaType, type Video, videoTypeLabel } from '../core/models'
import { repository } from '../data/store'
import { OutlineButton, TopBar } from '../ui/components'

/** Трейлер во встроенном плеере YouTube (youtube-nocookie.com — без отслеживающих cookie). */
export function TrailerScreen(props: { type: MediaType; id: number; video: string }) {
  const [videos, setVideos] = useState<Video[]>([])
  const [current, setCurrent] = useState(props.video)

  useEffect(() => {
    // Список роликов не обязателен: текущий трейлер играет и без него
    repository.videos(props.type, props.id).then(setVideos, () => {})
  }, [])

  const currentVideo = videos.find((v) => v.key === current)
  const src =
    `https://www.youtube-nocookie.com/embed/${encodeURIComponent(current)}` +
    '?playsinline=1&rel=0&modestbranding=1&hl=ru&cc_lang_pref=ru'

  return (
    <>
      <TopBar title={currentVideo?.name || 'Трейлер'} />
      <div class="scroll">
        <div class="player">
          <iframe
            key={current}
            src={src}
            title={currentVideo?.name || 'Трейлер'}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowFullScreen
            referrerpolicy="strict-origin-when-cross-origin"
          />
        </div>
        <div style={{ padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span class="t-small muted" style={{ flex: 1 }}>
            Не воспроизводится? Возможно, автор запретил встраивание или YouTube недоступен без VPN.
          </span>
          <OutlineButton
            text="YouTube"
            icon="openInNew"
            onClick={() => window.open(`https://www.youtube.com/watch?v=${encodeURIComponent(current)}`, '_blank', 'noopener')}
          />
        </div>
        {videos.length > 1 && (
          <>
            <div class="perforation" style={{ margin: '4px 16px 8px', flex: 'none', display: 'block' }} />
            {videos.map((v) => (
              <div
                key={v.key}
                class={`video-item press${v.key === current ? ' current' : ''}`}
                onClick={() => setCurrent(v.key)}
                role="button"
              >
                <div class="thumb">
                  <img src={`https://i.ytimg.com/vi/${v.key}/mqdefault.jpg`} alt="" loading="lazy" />
                </div>
                <div style={{ minWidth: '0px' }}>
                  <div class="t-tiny gold">
                    {videoTypeLabel(v.type)}
                    {v.language ? ` · ${v.language.toUpperCase()}` : ''}
                  </div>
                  <div class="t-small clamp2">{v.name}</div>
                </div>
              </div>
            ))}
          </>
        )}
      </div>
    </>
  )
}

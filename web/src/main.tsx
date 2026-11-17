import { render } from 'preact'
import { App } from './app'
import { flush } from './data/db'
import { init } from './data/store'
import { registerServiceWorker } from './pwa'
import { initNavigation } from './ui/nav'
import './styles.css'

initNavigation()
render(<App />, document.getElementById('app')!)
init()
registerServiceWorker()

// Перед уходом в фон дописываем незавершённые изменения (iOS может выгрузить приложение)
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') flush()
})

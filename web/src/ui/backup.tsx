import { useRef, useState } from 'preact/hooks'
import { BackupError, backupFileName, exportJson, importJson } from '../data/store'
import { OutlineButton, TextAction, toast } from './components'

/**
 * Сохраняет резервную копию. На iPhone открывается меню «Поделиться»:
 * файл можно сохранить в «Файлы», отправить в Telegram или на почту.
 */
export async function exportBackup() {
  const { json, count } = exportJson()
  const name = backupFileName()
  const file = new File([json], name, { type: 'application/json' })
  try {
    if (navigator.canShare?.({ files: [file] })) {
      await navigator.share({ files: [file], title: 'Резервная копия Pickture' })
      toast(`Сохранено тайтлов: ${count}`)
      return
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') return
    // Если «Поделиться» не сработало — скачиваем файл обычным способом
  }
  const url = URL.createObjectURL(file)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
  toast(`Файл ${name} сохранён · тайтлов: ${count}`)
}

function useImport(onImported?: () => void) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)

  async function onFile(file: File | undefined) {
    if (!file) return
    setBusy(true)
    try {
      const result = await importJson(await file.text())
      toast(
        result.total === 0
          ? 'В файле нет оценок'
          : `Восстановлено: ${result.applied} из ${result.total}` +
              (result.applied < result.total ? ' (остальные уже были новее)' : ''),
      )
      onImported?.()
    } catch (e) {
      toast(e instanceof BackupError ? e.message : 'Не удалось прочитать файл')
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  const element = (
    <input
      ref={input}
      type="file"
      accept=".json,application/json,text/plain"
      style={{ display: 'none' }}
      onChange={(e) => onFile((e.currentTarget as HTMLInputElement).files?.[0])}
    />
  )
  return { open: () => input.current?.click(), busy, element }
}

export function BackupImportButton(props: { label: string; onImported?: () => void; outline?: boolean }) {
  const imp = useImport(props.onImported)
  return (
    <>
      {imp.element}
      {props.outline ? (
        <OutlineButton text={props.label} icon="upload" onClick={imp.open} disabled={imp.busy} />
      ) : (
        <TextAction text={props.label} onClick={imp.open} disabled={imp.busy} />
      )}
    </>
  )
}

import { useCallback, useEffect, useRef, useState } from 'react'

/** How long a confirmed delete waits for Undo (V3 K9). */
export const UNDO_MS = 5000

interface Item {
  id: string
  name: string
}

/**
 * A delete that waits `UNDO_MS` before it is sent, because the backend has no undo (V3 K9). The item
 * is hidden from the moment it is confirmed. Confirming another one, leaving the page (unmount), or
 * closing the tab (`pagehide`, sent with `keepalive`) sends the waiting one at once, so a confirmed
 * delete is never lost. A failed delete shows the item again and reports the error.
 */
export function useUndoableDelete(
  remove: (id: string, options?: { keepalive?: boolean }) => Promise<void>,
) {
  const [pending, setPending] = useState<Item | null>(null)
  const [hidden, setHidden] = useState<ReadonlySet<string>>(new Set())
  const [failure, setFailure] = useState<{ name: string; error: unknown } | null>(null)
  const pendingRef = useRef<Item | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const removeRef = useRef(remove)
  useEffect(() => {
    removeRef.current = remove
  }, [remove])

  const send = useCallback((keepalive = false) => {
    const item = pendingRef.current
    if (!item) return
    clearTimeout(timer.current)
    pendingRef.current = null
    setPending(null)
    removeRef.current(item.id, keepalive ? { keepalive } : undefined).catch((error: unknown) => {
      setHidden((ids) => without(ids, item.id))
      setFailure({ name: item.name, error })
    })
  }, [])

  const confirm = useCallback(
    (item: Item) => {
      send()
      pendingRef.current = item
      setPending(item)
      setHidden((ids) => new Set(ids).add(item.id))
      setFailure(null)
      timer.current = setTimeout(() => send(), UNDO_MS)
    },
    [send],
  )

  const undo = useCallback(() => {
    const item = pendingRef.current
    if (!item) return
    clearTimeout(timer.current)
    pendingRef.current = null
    setPending(null)
    setHidden((ids) => without(ids, item.id))
  }, [])

  useEffect(() => {
    const onPageHide = () => send(true)
    window.addEventListener('pagehide', onPageHide)
    return () => {
      window.removeEventListener('pagehide', onPageHide)
      send(true)
    }
  }, [send])

  return {
    pending,
    hidden,
    failure,
    confirm,
    undo,
    dismissFailure: useCallback(() => setFailure(null), []),
  }
}

function without(ids: ReadonlySet<string>, id: string) {
  const next = new Set(ids)
  next.delete(id)
  return next
}

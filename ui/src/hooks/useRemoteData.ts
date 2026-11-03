import { useCallback, useEffect, useState } from 'react'

export interface RemoteData<T> {
  /** The latest result for the current `load`; kept while it reloads, cleared when `load` changes. */
  data: T | undefined
  loading: boolean
  error: unknown
  /** Loads again, e.g. after a change was saved. */
  reload: () => void
}

interface Settled<T> {
  load: (() => Promise<T>) | null
  version: number
  data?: T
  error?: unknown
}

/**
 * Runs `load` and keeps its result. Pass a stable function (`useCallback` / `useMemo`): a new one
 * starts a new load, and answers to an earlier one are ignored, so data never belongs to a
 * previous input. `null` loads nothing.
 */
export function useRemoteData<T>(load: (() => Promise<T>) | null): RemoteData<T> {
  const [version, setVersion] = useState(0)
  const [settled, setSettled] = useState<Settled<T>>({ load: null, version: -1 })

  useEffect(() => {
    if (!load) return
    let current = true
    load().then(
      (data) => current && setSettled({ load, version, data }),
      (error: unknown) => current && setSettled({ load, version, error }),
    )
    return () => {
      current = false
    }
  }, [load, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const sameInput = load !== null && settled.load === load
  return {
    data: sameInput ? settled.data : undefined,
    loading: load !== null && !(sameInput && settled.version === version),
    error: sameInput && settled.version === version ? settled.error : undefined,
    reload,
  }
}

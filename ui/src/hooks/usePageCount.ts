import { useState } from 'react'
import { useLocation } from 'react-router'

/**
 * Counts the pages the user moved to, for `AppRoutes` to key and fade in the content (ADR 061, M7).
 * It stays 0 on the first page, so nothing moves on page load. A navigation with `quiet` in its
 * state is not a page change to the user, like a new chat becoming its session.
 */
export function usePageCount(): number {
  const { pathname, state } = useLocation()
  const [page, setPage] = useState({ pathname, count: 0 })
  if (pathname !== page.pathname) {
    const quiet = (state as { quiet?: boolean } | null)?.quiet === true
    setPage({ pathname, count: quiet ? page.count : page.count + 1 })
  }
  return page.count
}

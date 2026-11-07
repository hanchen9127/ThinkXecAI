import { createContext, useContext } from 'react'

/**
 * The element a page scrolls in, `main` (ADR 063): the shell itself never scrolls. Null until `main`
 * is in the document, and outside `Content` (a component rendered on its own in a test).
 */
export const ScrollAreaContext = createContext<HTMLElement | null>(null)

export function useScrollArea(): HTMLElement | null {
  return useContext(ScrollAreaContext)
}

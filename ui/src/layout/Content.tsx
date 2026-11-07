import { type ReactNode, useEffect, useState } from 'react'
import { cx } from '../utils/cx'
import { ScrollAreaContext } from './scrollArea'

/** The id the skip link moves focus to. */
export const MAIN_ID = 'main'

/**
 * The page's own scroll area (ADR 063): the only part of the shell that scrolls for a page, so a long
 * list beside it never sets its height. The wrapper inside clips the page rising into place
 * (ADR 061), so that never adds to the scroll height. On a narrow screen a list route hides it.
 *
 * `page` counts the pages the user moved to (`usePageCount`): from the second one on, focus moves to
 * the new page's heading, so a keyboard or screen-reader user starts there and not on the link they
 * left (Phase 14, D6). Pages load lazily, so the heading may arrive a moment later.
 */
export default function Content({
  page,
  hiddenWhenNarrow,
  children,
}: {
  page: number
  hiddenWhenNarrow: boolean
  children: ReactNode
}) {
  const [area, setArea] = useState<HTMLElement | null>(null)

  useEffect(() => {
    if (page === 0 || !area) return
    const focusHeading = () => {
      const heading = area.querySelector('h1')
      if (!heading) return false
      if (!heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1')
      heading.focus({ preventScroll: true })
      return true
    }
    if (focusHeading()) return
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect()
    })
    observer.observe(area, { childList: true, subtree: true })
    const giveUp = setTimeout(() => observer.disconnect(), 5000)
    return () => {
      observer.disconnect()
      clearTimeout(giveUp)
    }
  }, [page, area])

  return (
    <main
      ref={setArea}
      id={MAIN_ID}
      // Focusable from the skip link only; it is not a stop in the tab order.
      tabIndex={-1}
      className={cx(
        'min-h-0 min-w-0 flex-1 flex-col overflow-y-auto outline-none wide:flex',
        hiddenWhenNarrow ? 'hidden' : 'flex',
      )}
    >
      <div className="flex flex-1 flex-col overflow-y-clip">
        <ScrollAreaContext value={area}>{children}</ScrollAreaContext>
      </div>
    </main>
  )
}

import { type ReactNode, useState } from 'react'
import { cx } from '../utils/cx'
import { ScrollAreaContext } from './scrollArea'

/**
 * The page's own scroll area (ADR 063): the only part of the shell that scrolls for a page, so a long
 * list beside it never sets its height. The wrapper inside clips the page rising into place
 * (ADR 061), so that never adds to the scroll height. On a narrow screen a list route hides it.
 */
export default function Content({
  hiddenWhenNarrow,
  children,
}: {
  hiddenWhenNarrow: boolean
  children: ReactNode
}) {
  const [area, setArea] = useState<HTMLElement | null>(null)
  return (
    <main
      ref={setArea}
      className={cx(
        'min-h-0 min-w-0 flex-1 flex-col overflow-y-auto wide:flex',
        hiddenWhenNarrow ? 'hidden' : 'flex',
      )}
    >
      <div className="flex flex-1 flex-col overflow-y-clip">
        <ScrollAreaContext value={area}>{children}</ScrollAreaContext>
      </div>
    </main>
  )
}

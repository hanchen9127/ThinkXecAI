import type { ReactNode } from 'react'
import SideMenu from '../components/SideMenu'
import Wordmark from '../components/Wordmark'
import { cx } from '../utils/cx'
import type { Section } from './sections'

/**
 * The rail plus the list panel of the open section. The panel scrolls on its own (ADR 063); on a
 * narrow screen it fills the screen on a list route and is hidden on an item.
 */
export default function Sidebar({
  active,
  hiddenWhenNarrow,
  children,
}: {
  active: Section | null
  hiddenWhenNarrow: boolean
  children: ReactNode
}) {
  return (
    <>
      <SideMenu active={active} />
      <aside
        className={cx(
          'box-border min-h-0 flex-1 flex-col gap-5 overflow-y-auto bg-surface px-6 pt-5.5 pb-7',
          'wide:flex wide:flex-none wide:basis-panel wide:border-r wide:border-line',
          hiddenWhenNarrow ? 'hidden' : 'flex',
        )}
      >
        <Wordmark className="hidden wide:block" />
        {children}
      </aside>
    </>
  )
}

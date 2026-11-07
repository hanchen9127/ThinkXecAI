import type { ReactNode } from 'react'

/**
 * The shell, as tall as the window and never scrolling itself (ADR 063): the rail, the list panel,
 * and the page side by side, each scrolling on its own. On a narrow screen the rail is a bar on top
 * and one of the other two fills the rest.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-ground font-sans text-ink wide:flex-row">
      {children}
    </div>
  )
}

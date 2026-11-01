import type { ReactNode } from 'react'

/** Side menu and content in one row; at phone width the content wraps below the menu. */
export default function Layout({ children }: { children: ReactNode }) {
  return <div className="flex min-h-screen flex-wrap bg-ground font-sans text-ink">{children}</div>
}

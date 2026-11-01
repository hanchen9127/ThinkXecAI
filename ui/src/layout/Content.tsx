import type { ReactNode } from 'react'

// grow-999 leaves the sidebar at its basis and gives the content every spare pixel; below
// panel + content width the row wraps and the content takes the full width.
export default function Content({ children }: { children: ReactNode }) {
  return <main className="flex min-w-0 grow-999 basis-content flex-col">{children}</main>
}

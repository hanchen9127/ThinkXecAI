import type { ReactNode } from 'react'

/**
 * A tool or a knowledge base named in a list or a setup, as text after a small mark (ADR 058): a tool
 * name in mono after an Execute-orange square, a knowledge base after a document glyph.
 */
export default function Tag({
  kind,
  children,
}: {
  kind: 'tool' | 'knowledge'
  children: ReactNode
}) {
  if (kind === 'tool') {
    return (
      <span className="inline-flex items-center gap-1.25 font-mono text-xs text-ink">
        <span aria-hidden="true" className="size-1.5 shrink-0 bg-execute" />
        {children}
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1.25 text-sm text-ink">
      <svg
        aria-hidden="true"
        width="11"
        height="12"
        viewBox="0 0 11 12"
        fill="none"
        className="shrink-0 stroke-muted"
        strokeWidth="1.3"
      >
        <path d="M1.5 1.5h6l2 2v7h-8z" />
      </svg>
      {children}
    </span>
  )
}

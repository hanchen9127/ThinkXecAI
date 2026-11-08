import type { ReactNode } from 'react'
import { cx } from '../utils/cx'

/**
 * A tool or a knowledge base named in a list or a setup (ADR 058). A tool is an outlined token with
 * its label (ADR 065): solid when the agent's setup chose it, dashed when every agent has it. A
 * knowledge base is text after a document glyph.
 */
export default function Tag({
  kind,
  always = false,
  children,
}: {
  kind: 'tool' | 'knowledge'
  /** A tool every agent has (a fixed tool). */
  always?: boolean
  children: ReactNode
}) {
  if (kind === 'tool') {
    return (
      <span
        data-always={always || undefined}
        className={cx(
          'inline-flex items-center rounded-xs border px-1.75 text-xs leading-5 whitespace-nowrap',
          always
            ? 'border-dashed border-field/70 text-muted'
            : 'border-field/50 bg-surface text-ink',
        )}
      >
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

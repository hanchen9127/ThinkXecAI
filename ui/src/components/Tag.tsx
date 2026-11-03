import type { ReactNode } from 'react'
import { cx } from '../utils/cx'

/** A tool (Execute colours) or a knowledge base (Think colours), named in a list or a setup. */
export default function Tag({
  kind,
  children,
}: {
  kind: 'tool' | 'knowledge'
  children: ReactNode
}) {
  return (
    <span
      className={cx(
        'rounded-ui px-2 text-xs',
        kind === 'tool' ? 'bg-execute-wash text-execute-ink' : 'bg-think-wash text-think-ink',
      )}
    >
      {children}
    </span>
  )
}

import type { ReactNode } from 'react'

/** A list panel's title row, with an optional action on the right. */
export default function TabHeading({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <h2 className="m-0 text-xl font-semibold">{title}</h2>
      {action}
    </div>
  )
}

import { type ReactNode, useState } from 'react'

/**
 * Content that opens and closes by easing its height (ADR 061; the motion is `.expandable` in
 * index.css). It mounts its content on first open and keeps it while closed, so closing plays out;
 * closed, it is hidden from assistive technology and cannot be focused. `motion` picks the form:
 * "slide" for results and steps, "fold" for a row that leaves, "rise" for the Undo bar.
 */
export default function Expandable({
  open,
  motion,
  as: Tag = 'div',
  className,
  children,
}: {
  open: boolean
  motion: 'slide' | 'fold' | 'rise'
  as?: 'div' | 'li'
  /** On the content, inside the clipping wrapper, so borders fold away with it. */
  className?: string
  children: ReactNode
}) {
  const [opened, setOpened] = useState(open)
  if (open && !opened) setOpened(true)

  return (
    <Tag
      className="expandable"
      data-motion={motion}
      data-open={open || undefined}
      aria-hidden={open ? undefined : true}
      inert={!open}
    >
      <div>
        <div className={className}>{opened && children}</div>
      </div>
    </Tag>
  )
}

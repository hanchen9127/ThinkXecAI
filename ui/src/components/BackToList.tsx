import { LeftOutlined } from '@ant-design/icons'
import { Link, useLocation } from 'react-router'
import { LISTS, sectionOf } from '../layout/sections'
import { cx } from '../utils/cx'

/**
 * On a narrow screen an item shows alone, so its header starts with a way back to its section's list
 * (ADR 063): a 44px arrow before the title. Not rendered on a wide screen, where the list is beside it.
 */
export default function BackToList({ className }: { className?: string }) {
  const section = sectionOf(useLocation().pathname)
  if (!section) return null
  const list = LISTS[section]
  return (
    <Link
      to={list.to}
      aria-label={`Back to ${list.name}`}
      className={cx(
        'flex size-11 shrink-0 items-center justify-center rounded-ui text-ink no-underline wide:hidden',
        className,
      )}
    >
      <LeftOutlined aria-hidden="true" />
    </Link>
  )
}

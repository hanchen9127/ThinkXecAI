import { BookOutlined, MessageOutlined, RobotOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { Section } from '../layout/sections'
import { cx } from '../utils/cx'

const ITEMS: { section: Section; label: string; to: string; icon: ReactNode }[] = [
  { section: 'agents', label: 'Agents', to: '/agent', icon: <RobotOutlined /> },
  { section: 'chats', label: 'Chats', to: '/chat', icon: <MessageOutlined /> },
  {
    section: 'knowledgeBases',
    label: 'Knowledge bases',
    to: '/knowledge-base',
    icon: <BookOutlined />,
  },
]

/** The icon rail: one link per section, the open one marked. */
export default function SideMenu({ active }: { active: Section | null }) {
  return (
    <nav
      aria-label="Main"
      className="flex shrink-0 basis-16 flex-col items-center gap-2.5 bg-rail py-5"
    >
      {ITEMS.map((item) => {
        const current = item.section === active
        return (
          <Link
            key={item.section}
            to={item.to}
            aria-label={item.label}
            title={item.label}
            aria-current={current ? 'page' : undefined}
            className={cx(
              'flex size-11 items-center justify-center rounded-ui text-icon',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-think',
              current ? 'bg-rail-active text-white' : 'text-rail-icon hover:text-white',
            )}
          >
            {item.icon}
          </Link>
        )
      })}
    </nav>
  )
}

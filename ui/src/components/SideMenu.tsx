import { BookOutlined, DeploymentUnitOutlined, MessageOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { Section } from '../layout/sections'
import { cx } from '../utils/cx'

const ITEMS: { section: Section; label: string; short: string; to: string; icon: ReactNode }[] = [
  {
    section: 'agents',
    label: 'Agents',
    short: 'Agents',
    to: '/agent',
    icon: <DeploymentUnitOutlined />,
  },
  { section: 'chats', label: 'Chats', short: 'Chats', to: '/chat', icon: <MessageOutlined /> },
  {
    section: 'knowledgeBases',
    label: 'Knowledge bases',
    short: 'Knowledge',
    to: '/knowledge-base',
    icon: <BookOutlined />,
  },
]

/** The rail: one labelled link per section, the open one marked; a white focus ring (ADR 058). */
export default function SideMenu({ active }: { active: Section | null }) {
  return (
    <nav
      aria-label="Main"
      className="flex shrink-0 basis-19 flex-col items-center gap-2 bg-rail py-4"
    >
      {ITEMS.map((item) => {
        const current = item.section === active
        return (
          <Link
            key={item.section}
            to={item.to}
            aria-label={item.label}
            aria-current={current ? 'page' : undefined}
            className={cx(
              'flex h-12.5 w-15 flex-col items-center justify-center gap-0.75 rounded-ui no-underline',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white',
              current ? 'bg-rail-active text-white' : 'text-rail-icon hover:text-white',
            )}
          >
            <span className="text-icon leading-none">{item.icon}</span>
            <span aria-hidden="true" className="text-label">
              {item.short}
            </span>
          </Link>
        )
      })}
    </nav>
  )
}

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

/**
 * The rail: one link per section, the open one marked, with a white focus ring (ADR 058). On a wide
 * screen a column of labelled icons; on a narrow one a bar on top with the product name and the
 * icons alone, each still named for screen readers (ADR 063).
 */
export default function SideMenu({ active }: { active: Section | null }) {
  return (
    <div className="flex h-13 shrink-0 items-center justify-between bg-rail pr-2 pl-4 wide:h-auto wide:basis-19 wide:flex-col wide:justify-start wide:px-0 wide:py-4">
      <p className="m-0 text-sm font-semibold text-white wide:hidden">ThinkXecAI</p>
      <nav aria-label="Main" className="flex gap-0.5 wide:flex-col wide:items-center wide:gap-2">
        {ITEMS.map((item) => {
          const current = item.section === active
          return (
            <Link
              key={item.section}
              to={item.to}
              aria-label={item.label}
              aria-current={current ? 'page' : undefined}
              className={cx(
                'flex size-11 flex-col items-center justify-center gap-0.75 rounded-ui no-underline wide:h-12.5 wide:w-15',
                'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white',
                current ? 'bg-rail-active text-white' : 'text-rail-icon hover:text-white',
              )}
            >
              <span className="text-icon leading-none">{item.icon}</span>
              <span aria-hidden="true" className="hidden text-label wide:inline">
                {item.short}
              </span>
            </Link>
          )
        })}
      </nav>
    </div>
  )
}

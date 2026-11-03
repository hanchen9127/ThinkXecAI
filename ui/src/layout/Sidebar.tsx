import type { ReactNode } from 'react'
import SideMenu from '../components/SideMenu'
import Wordmark from '../components/Wordmark'
import type { Section } from './sections'

/** The icon rail plus the list panel of the open section. */
export default function Sidebar({
  active,
  children,
}: {
  active: Section | null
  children: ReactNode
}) {
  return (
    <>
      <SideMenu active={active} />
      <aside className="box-border flex max-w-80 grow basis-panel flex-col gap-5 border-r border-line bg-surface px-6 pt-5.5 pb-7">
        <Wordmark />
        {children}
      </aside>
    </>
  )
}

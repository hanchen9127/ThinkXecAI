import { MoreOutlined } from '@ant-design/icons'
import { Button, Dropdown } from 'antd'
import { useState } from 'react'
import { Link, matchPath, useLocation } from 'react-router'
import type { Agent, KnowledgeBase } from '../../api/api'
import { path } from '../../api/http'
import { useAgents } from '../../hooks/useAgents'
import { useMenuTrigger } from '../../hooks/useMenuTrigger'
import { useKnowledgeBaseDialogs } from '../../hooks/useKnowledgeBaseDialogs'
import { useKnowledgeBases } from '../../hooks/useKnowledgeBases'
import { cx } from '../../utils/cx'
import { agentsUsing } from '../knowledgeBases/knowledgeBases'
import TabHeading from './TabHeading'
import LoadError from '../LoadError'

/** The knowledge-base list (V3 K1 C): each name, and the agents that search it on demand. */
export default function KnowledgeBaseTabContent() {
  const { knowledgeBases, loading, error, reload } = useKnowledgeBases()
  const { agents } = useAgents()
  const openId = matchPath('/knowledge-base/:knowledgeBaseId', useLocation().pathname)?.params
    .knowledgeBaseId
  const { openCreate, openEdit, openDelete, dialogs } = useKnowledgeBaseDialogs()

  return (
    <section aria-label="Knowledge bases" className="flex flex-col gap-5">
      <TabHeading
        title="Knowledge bases"
        action={
          <Button type="primary" aria-label="New knowledge base" onClick={openCreate}>
            New
          </Button>
        }
      />
      {error !== undefined && <LoadError what="knowledge bases" error={error} onRetry={reload} />}
      {loading && knowledgeBases.length === 0 && (
        <p className="m-0 text-muted">Loading knowledge bases…</p>
      )}
      {!loading && error === undefined && knowledgeBases.length === 0 && (
        <p className="m-0 text-muted">No knowledge bases yet.</p>
      )}
      {knowledgeBases.length > 0 && (
        <ul className="m-0 list-none p-0">
          {knowledgeBases.map((kb) => (
            <KnowledgeBaseItem
              key={kb.id}
              knowledgeBase={kb}
              users={agentsUsing(agents, kb.id)}
              open={kb.id === openId}
              onEdit={() => openEdit(kb)}
              onDelete={() => openDelete(kb)}
            />
          ))}
        </ul>
      )}
      {dialogs}
    </section>
  )
}

function KnowledgeBaseItem({
  knowledgeBase,
  users,
  open,
  onEdit,
  onDelete,
}: {
  knowledgeBase: KnowledgeBase
  users: Agent[]
  open: boolean
  onEdit: () => void
  onDelete: () => void
}) {
  const [shown, setShown] = useState(false)
  // Focus goes back to ⋯ when the dialog an item opens is closed (Phase 14, D6).
  const { trigger: menuButton, then: focusMenuThen } = useMenuTrigger()

  return (
    <li
      className={cx(
        'border-b border-hairline py-3.5 pl-3 transition active:scale-98',
        open ? 'bg-ground' : 'hover:bg-ground',
      )}
    >
      <div className="flex items-center gap-2">
        <Link
          to={path`/knowledge-base/${knowledgeBase.id}`}
          aria-current={open ? 'page' : undefined}
          className={cx(
            'min-w-0 flex-1 text-ink no-underline hover:underline',
            open && 'font-semibold',
          )}
        >
          {knowledgeBase.name}
        </Link>
        <Dropdown
          trigger={['click']}
          autoFocus
          menu={{
            items: [
              { key: 'edit', label: 'Edit', onClick: focusMenuThen(onEdit) },
              { key: 'delete', label: 'Delete', danger: true, onClick: focusMenuThen(onDelete) },
            ],
          }}
        >
          <Button
            ref={menuButton}
            type="text"
            size="small"
            aria-label={`More actions for ${knowledgeBase.name}`}
            icon={<MoreOutlined />}
          />
        </Dropdown>
      </div>
      {users.length === 0 ? (
        <p className="m-0 text-xs text-muted">Not used by any agent</p>
      ) : (
        <button
          type="button"
          aria-expanded={shown}
          onClick={() => setShown(!shown)}
          className="cursor-pointer text-xs text-ink underline decoration-dotted"
        >
          Used by {users.length === 1 ? '1 agent' : `${users.length} agents`}
        </button>
      )}
      {shown && users.length > 0 && (
        <ul
          aria-label={`Agents using ${knowledgeBase.name}`}
          className="m-0 mt-2 flex list-none flex-wrap gap-1.5 p-0"
        >
          {users.map((agent) => (
            <li key={agent.id}>
              <Link
                to={path`/agent/${agent.id}`}
                className="rounded-ui border border-line px-2 text-xs text-ink no-underline hover:underline"
              >
                {agent.name}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}

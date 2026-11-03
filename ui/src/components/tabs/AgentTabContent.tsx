import { MoreOutlined } from '@ant-design/icons'
import { Button, Dropdown } from 'antd'
import { useState } from 'react'
import { Link, matchPath, useLocation } from 'react-router'
import type { Agent } from '../../api/api'
import { path } from '../../api/http'
import { useAgentDialogs } from '../../hooks/useAgentDialogs'
import { useAgents } from '../../hooks/useAgents'
import { useKnowledgeBaseNames } from '../../hooks/useKnowledgeBaseNames'
import { cx } from '../../utils/cx'
import { countLabel, errorMessage } from '../../utils/text'
import Tag from '../Tag'
import TabHeading from './TabHeading'

/** The agent list (V2 A1 D): a short summary per agent whose counts open into names. */
export default function AgentTabContent() {
  const { agents, loading, error, reload } = useAgents()
  const openId = matchPath('/agent/:agentId', useLocation().pathname)?.params.agentId
  const { openCreate, openEdit, openDelete, dialogs } = useAgentDialogs()

  return (
    <section aria-label="Agents" className="flex flex-col gap-5">
      <TabHeading
        title="Agents"
        action={
          <Button type="primary" onClick={openCreate}>
            New agent
          </Button>
        }
      />
      {error !== undefined && (
        <div role="alert" className="flex flex-col items-start gap-2 text-muted">
          <p className="m-0">Couldn't load agents. {errorMessage(error)}</p>
          <button type="button" onClick={reload} className="cursor-pointer text-ink underline">
            Try again
          </button>
        </div>
      )}
      {loading && agents.length === 0 && <p className="m-0 text-muted">Loading agents…</p>}
      {!loading && error === undefined && agents.length === 0 && (
        <p className="m-0 text-muted">No agents yet.</p>
      )}
      {agents.length > 0 && (
        <ul className="m-0 list-none p-0">
          {agents.map((agent) => (
            <AgentItem
              key={agent.id}
              agent={agent}
              open={agent.id === openId}
              onEdit={() => openEdit(agent)}
              onDelete={() => openDelete(agent)}
            />
          ))}
        </ul>
      )}
      {dialogs}
    </section>
  )
}

function AgentItem({
  agent,
  open,
  onEdit,
  onDelete,
}: {
  agent: Agent
  open: boolean
  onEdit: () => void
  onDelete: () => void
}) {
  const kbNames = useKnowledgeBaseNames()
  const [shown, setShown] = useState<'tools' | 'knowledge' | null>(null)
  const toggle = (part: 'tools' | 'knowledge') => setShown(shown === part ? null : part)

  return (
    <li
      className={cx(
        'border-b border-hairline py-3.5 pl-3 transition active:scale-98',
        open ? 'bg-ground' : 'hover:bg-ground',
      )}
    >
      <div className="flex items-center gap-2">
        <Link
          to={path`/agent/${agent.id}`}
          aria-current={open ? 'page' : undefined}
          className={cx(
            'min-w-0 flex-1 text-ink no-underline hover:underline',
            open && 'font-semibold',
          )}
        >
          {agent.name}
        </Link>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: 'edit', label: 'Edit', onClick: onEdit },
              { key: 'delete', label: 'Delete', danger: true, onClick: onDelete },
            ],
          }}
        >
          <Button
            type="text"
            size="small"
            aria-label={`More actions for ${agent.name}`}
            icon={<MoreOutlined />}
          />
        </Dropdown>
      </div>
      <p className="m-0 text-xs text-muted">
        <span className="font-mono">{agent.model}</span>,{' '}
        <CountToggle
          count={agent.allowedTools.length}
          one="tool"
          many="tools"
          expanded={shown === 'tools'}
          onToggle={() => toggle('tools')}
        />
        ,{' '}
        <CountToggle
          count={agent.allowedKbs.length}
          one="knowledge base"
          many="knowledge bases"
          expanded={shown === 'knowledge'}
          onToggle={() => toggle('knowledge')}
        />
      </p>
      {shown === 'tools' && (
        <TagRow kind="tool" names={agent.allowedTools} label={`Tools of ${agent.name}`} />
      )}
      {shown === 'knowledge' && (
        <TagRow
          kind="knowledge"
          names={kbNames(agent.allowedKbs)}
          label={`Knowledge bases of ${agent.name}`}
        />
      )}
    </li>
  )
}

/** A count that opens its names; zero has nothing to open and stays plain text. */
function CountToggle(props: {
  count: number
  one: string
  many: string
  expanded: boolean
  onToggle: () => void
}) {
  const label = countLabel(props.count, props.one, props.many)
  if (props.count === 0) return <span>{label}</span>
  return (
    <button
      type="button"
      aria-expanded={props.expanded}
      onClick={props.onToggle}
      className="inline cursor-pointer text-ink underline decoration-dotted"
    >
      {label}
    </button>
  )
}

function TagRow({
  kind,
  names,
  label,
}: {
  kind: 'tool' | 'knowledge'
  names: string[]
  label: string
}) {
  return (
    <ul aria-label={label} className="m-0 mt-2 flex list-none flex-wrap gap-1.5 p-0">
      {names.map((name, i) => (
        <li key={`${name}-${i}`}>
          <Tag kind={kind}>{name}</Tag>
        </li>
      ))}
    </ul>
  )
}

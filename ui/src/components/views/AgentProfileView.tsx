import { Button } from 'antd'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useAgentDialogs } from '../../hooks/useAgentDialogs'
import { useAgents } from '../../hooks/useAgents'
import { useKnowledgeBaseNames } from '../../hooks/useKnowledgeBaseNames'
import { COLUMN, ITEM_HEADER } from '../../layout/column'
import BackToList from '../BackToList'
import { cx } from '../../utils/cx'
import { formatApiTimestamp } from '../../utils/time'
import DeleteMenu from '../DeleteMenu'
import { useAgentTools } from '../../hooks/useAgentTools'
import Tag from '../Tag'
import LoadError from '../LoadError'

/** `/agent/:agentId`: how one agent is set up, and a way to start chatting with it (V2 A3 B). */
export default function AgentProfileView() {
  const { agentId } = useParams()
  const { agents, loading, error } = useAgents()
  const kbNames = useKnowledgeBaseNames()
  const { openEdit, openDelete, dialogs } = useAgentDialogs()
  const agent = agents.find((a) => a.id === agentId)
  const tools = useAgentTools(agent?.allowedTools ?? [])

  if (!agent) {
    return (
      <div className={cx(COLUMN, 'flex flex-col gap-3 py-9')}>
        {loading ? (
          <p className="m-0 text-muted">Loading the agent…</p>
        ) : error !== undefined ? (
          <LoadError what="agents" error={error} />
        ) : (
          <>
            <h1 className="m-0 text-xl font-semibold">Agent not found</h1>
            <p className="m-0 text-muted">It may have been deleted.</p>
            <Link to="/agent" className="text-ink underline">
              Back to agents
            </Link>
          </>
        )}
      </div>
    )
  }

  const { chatOptions } = agent
  return (
    <>
      <header className={ITEM_HEADER}>
        <div className={cx(COLUMN, 'flex flex-wrap items-start justify-between gap-4 pt-6 pb-5')}>
          <div className="flex min-w-0 items-start gap-1">
            <BackToList className="-mt-2 -ml-3" />
            <div className="min-w-0">
              <h1 className="m-0 text-xl font-semibold">{agent.name}</h1>
              {agent.description && (
                <p className="m-0 mt-0.5 text-sm text-muted">{agent.description}</p>
              )}
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => openEdit(agent)}>Edit</Button>
            <Link
              to={`/chat/new?${new URLSearchParams({ agent: agent.id })}`}
              className="flex h-8 items-center rounded-ui bg-ink px-4 text-sm font-semibold text-white no-underline hover:bg-ink/85"
            >
              Start chat
            </Link>
            <DeleteMenu onDelete={() => openDelete(agent)} />
          </div>
        </div>
      </header>
      {dialogs}

      <section aria-label="Setup" className={cx(COLUMN, 'py-8')}>
        <dl className="m-0 rounded-ui border border-line bg-surface px-4 py-1 wide:px-7">
          <Row term="Model">
            <span className="font-mono text-xs text-muted">{agent.model}</span>
          </Row>
          <Row term="Tools">
            {tools.error !== undefined ? (
              <LoadError what="the tools" error={tools.error} onRetry={tools.reload} />
            ) : !tools.known ? (
              <span className="text-muted">Loading tools…</span>
            ) : tools.count > 0 ? (
              tools.tags
            ) : (
              <span className="text-muted">None</span>
            )}
          </Row>
          <Row term="Knowledge bases">
            {agent.allowedKbs.length > 0 ? (
              kbNames(agent.allowedKbs).map((name, i) => (
                <Tag key={`${name}-${i}`} kind="knowledge">
                  {name}
                </Tag>
              ))
            ) : (
              <span className="text-muted">None</span>
            )}
          </Row>
          <Row term="Remembered messages">The last {chatOptions.messageLength}</Row>
          <Row term="Temperature, top P">
            {chatOptions.temperature.toFixed(1)}, {chatOptions.topP.toFixed(2)}
            <span className="text-xs text-muted">saved, not yet used at run time</span>
          </Row>
          <Row term="Instructions" last>
            {agent.systemPrompt ? (
              <span className="whitespace-pre-line">{agent.systemPrompt}</span>
            ) : (
              <span className="text-muted">None</span>
            )}
          </Row>
        </dl>
        <p className="m-0 mt-3.5 text-xs text-muted">
          Updated {formatApiTimestamp(agent.updatedAt)}
        </p>
      </section>
    </>
  )
}

function Row({ term, last, children }: { term: string; last?: boolean; children: ReactNode }) {
  return (
    <div className={cx('flex flex-wrap gap-x-4 gap-y-1 py-4', !last && 'border-b border-hairline')}>
      {/* A narrow screen puts the value under its label instead of squeezing it (ADR 063). */}
      <dt className="w-full shrink-0 text-muted wide:w-50">{term}</dt>
      <dd className="m-0 flex min-w-0 flex-1 flex-wrap items-center gap-1.5">{children}</dd>
    </div>
  )
}

import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useAgents } from '../../hooks/useAgents'
import { useKnowledgeBaseNames } from '../../hooks/useKnowledgeBaseNames'
import { cx } from '../../utils/cx'
import { formatApiTimestamp } from '../../utils/time'
import { errorMessage } from '../../utils/text'
import Tag from '../Tag'

/** `/agent/:agentId`: how one agent is set up, and a way to start chatting with it (V2 A3 B). */
export default function AgentProfileView() {
  const { agentId } = useParams()
  const { agents, loading, error } = useAgents()
  const kbNames = useKnowledgeBaseNames()
  const agent = agents.find((a) => a.id === agentId)

  if (!agent) {
    return (
      <div className="flex flex-col gap-3 px-12 py-9">
        {loading ? (
          <p className="m-0 text-muted">Loading the agent…</p>
        ) : error !== undefined ? (
          <p role="alert" className="m-0 text-muted">
            Couldn't load agents. {errorMessage(error)}
          </p>
        ) : (
          <>
            <h1 className="m-0 text-xl font-semibold">Agent not found</h1>
            <p className="m-0 text-muted">It may have been deleted.</p>
            <Link to="/agent" className="text-think">
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
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-line bg-surface px-12 pt-6 pb-5">
        <div className="min-w-0">
          <h1 className="m-0 text-xl font-semibold">{agent.name}</h1>
          {agent.description && (
            <p className="m-0 mt-0.5 text-sm text-muted">{agent.description}</p>
          )}
        </div>
        <Link
          to={`/chat?${new URLSearchParams({ agent: agent.id })}`}
          className="flex h-9 items-center rounded-ui bg-think px-4 font-semibold text-white no-underline hover:bg-think-ink"
        >
          Start chat
        </Link>
      </header>

      <section aria-label="Setup" className="box-border w-full max-w-220 px-12 py-8">
        <dl className="m-0 rounded-ui border border-line bg-surface px-7 py-1">
          <Row term="Model">{agent.model}</Row>
          <Row term="Tools">
            {agent.allowedTools.map((tool) => (
              <Tag key={tool} kind="tool">
                {tool}
              </Tag>
            ))}
            <span className="text-xs text-muted">
              {agent.allowedTools.length > 0 ? 'plus ' : ''}knowledge search, date, city, weather
            </span>
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
    <div className={cx('flex flex-wrap gap-x-4 py-4', !last && 'border-b border-hairline')}>
      <dt className="w-50 shrink-0 text-muted">{term}</dt>
      <dd className="m-0 flex min-w-0 flex-1 flex-wrap items-center gap-1.5">{children}</dd>
    </div>
  )
}

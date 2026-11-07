import { Alert, Button, Select } from 'antd'
import { useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { type ChatSession, createChatMessage, stopChatSession } from '../../api/api'
import { ApiError, path } from '../../api/http'
import { useAgentDialogs } from '../../hooks/useAgentDialogs'
import { useAgents } from '../../hooks/useAgents'
import { useChatMessages } from '../../hooks/useChatMessages'
import { useChatSessions } from '../../hooks/useChatSessions'
import { useChatStream } from '../../hooks/useChatStream'
import { useFollowLatest } from '../../hooks/useFollowLatest'
import { newestMessage, useWorking } from '../../hooks/useWorking'
import { COLUMN } from '../../layout/column'
import BackToList from '../BackToList'
import { cx } from '../../utils/cx'
import { errorMessage } from '../../utils/text'
import AgentRun, { StepsExplained } from '../chat/AgentRun'
import Composer from '../chat/Composer'
import { chatTitle, groupHistory, retryQuestion, titleFrom } from '../chat/messages'

/** `/`, `/chat`, and `/chat/new` (a new chat, `?agent=<id>` chooses its agent), and `/chat/:chatSessionId`. */
export default function AgentChatView() {
  const { chatSessionId } = useParams()
  if (!chatSessionId) return <NewChat />
  // A new page per chat, so nothing typed or sent in one shows in another.
  return <Chat key={chatSessionId} sessionId={chatSessionId} />
}

/**
 * A chat before its first message (V4 C1 C). Sending creates the session, then the message, then
 * opens the chat; the starting design navigated first and lost the message.
 */
function NewChat() {
  const { agents, loading, error: agentsError, reload: reloadAgents } = useAgents()
  // Without an agent, "Create an agent" opens the form here: a first run needs no detour (Phase 14).
  const { openCreate, dialogs } = useAgentDialogs()
  const chatSessions = useChatSessions()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  // Set when the open chat was deleted elsewhere (V4 C5 C).
  const leftDeleted = (useLocation().state as { deleted?: boolean } | null)?.deleted === true
  const [chosen, setChosen] = useState<string>()
  const [sendError, setSendError] = useState<unknown>()
  // A session created for a message that then failed: sending again uses it, not a second one.
  const created = useRef<ChatSession | null>(null)

  const requested = params.get('agent')
  const agentId = chosen ?? (agents.some((a) => a.id === requested) ? requested! : agents[0]?.id)
  const agent = agents.find((a) => a.id === agentId)

  if (!agent) {
    return (
      <div className={cx(COLUMN, 'flex flex-col gap-2 py-9')}>
        <BackToList className="-mt-5 -ml-3" />
        {loading ? (
          <p className="m-0 text-muted">Loading agents…</p>
        ) : agentsError !== undefined ? (
          // An outage is not "none yet": say so, and let the user try again (Phase 14, D5).
          <>
            <h1 className="m-0 text-xl font-semibold">New chat</h1>
            <div role="alert" className="flex flex-col items-start gap-2 text-muted">
              <p className="m-0">Couldn't load agents. {errorMessage(agentsError)}</p>
              <button
                type="button"
                onClick={reloadAgents}
                className="cursor-pointer text-ink underline"
              >
                Try again
              </button>
            </div>
          </>
        ) : (
          <>
            <h1 className="m-0 text-xl font-semibold">New chat</h1>
            <p className="m-0 text-muted">
              A chat needs an agent to answer, and there is none yet.
            </p>
            <div>
              <Button type="primary" onClick={openCreate}>
                Create an agent
              </Button>
            </div>
          </>
        )}
        {dialogs}
      </div>
    )
  }

  async function send(content: string) {
    if (!agent) return
    setSendError(undefined)
    try {
      if (created.current?.agentId !== agent.id) {
        created.current = await chatSessions.create({
          agentId: agent.id,
          title: titleFrom(content),
        })
      }
      const session = created.current
      await createChatMessage({ sessionId: session.id, content })
      chatSessions.reload()
      navigate(path`/chat/${session.id}`, { state: { quiet: true } })
    } catch (error) {
      setSendError(error)
      throw error
    }
  }

  return (
    <>
      {leftDeleted && (
        <div role="status" className="border-b border-line bg-surface text-sm text-muted">
          <div className={cx(COLUMN, 'py-2.5')}>The chat you had open was deleted.</div>
        </div>
      )}
      <div className={cx(COLUMN, 'pt-2 wide:hidden')}>
        <BackToList className="-ml-3" />
      </div>
      <div className="box-border flex flex-1 flex-col justify-center px-4 py-12 wide:px-12">
        <div className="mx-auto flex w-full max-w-180 flex-col gap-4">
          <div className="flex flex-col gap-2">
            <h1 className="m-0 text-3xl font-semibold tracking-tight">
              What should {agent.name} look into?
            </h1>
            <StepsExplained />
          </div>
          {sendError !== undefined && (
            <Alert type="error" showIcon title={errorMessage(sendError)} />
          )}
          <Composer
            onSend={send}
            tools={
              <span className="flex items-center gap-2 text-sm text-muted">
                Agent
                <Select
                  aria-label="Agent"
                  size="small"
                  className="min-w-40"
                  popupMatchSelectWidth={false}
                  value={agent.id}
                  onChange={(id) => setChosen(id)}
                  options={agents.map((a) => ({ value: a.id, label: a.name }))}
                />
              </span>
            }
          />
        </div>
      </div>
    </>
  )
}

function Chat({ sessionId }: { sessionId: string }) {
  const chatSessions = useChatSessions()
  const agents = useAgents()
  const navigate = useNavigate()
  const { messages, loading, error, reload, send, receive } = useChatMessages(sessionId)
  const [sendError, setSendError] = useState<unknown>()
  const history = useMemo(() => groupHistory(messages), [messages])
  const newest = useMemo(() => newestMessage(messages), [messages])
  // The newest message when a stop found no run (a backend restart ended it): nothing will follow.
  const [abandoned, setAbandoned] = useState<string>()
  const working = useWorking(messages) && abandoned !== newest?.id
  const since = newest?.createdAt
  const [stopping, setStopping] = useState(false)
  // The run Try again was used on: a second click must not send its question twice.
  const retried = useRef<string>(undefined)
  // "Stopping…" lasts until the run ends with its notice.
  if (stopping && !working) setStopping(false)

  const session = chatSessions.chatSessions.find((s) => s.id === sessionId)
  const follow = useFollowLatest(`${session !== undefined}:${messages.length}:${working}`)
  const stream = useChatStream(
    sessionId,
    {
      onInit: reload,
      onMessage: receive,
      onGone: () => {
        chatSessions.reload()
        navigate('/chat/new', { replace: true, state: { deleted: true } })
      },
    },
    session !== undefined,
  )
  if (!session) {
    return (
      <div className={cx(COLUMN, 'flex flex-col gap-3 py-9')}>
        <BackToList className="-mt-5 -ml-3" />
        {chatSessions.loading ? (
          <p className="m-0 text-muted">Loading the chat…</p>
        ) : chatSessions.error !== undefined ? (
          <p role="alert" className="m-0 text-muted">
            Couldn't load chats. {errorMessage(chatSessions.error)}
          </p>
        ) : (
          <>
            <h1 className="m-0 text-xl font-semibold">Chat not found</h1>
            <p className="m-0 text-muted">It may have been deleted.</p>
            <Link to="/chat/new" className="text-ink underline">
              Start a new chat
            </Link>
          </>
        )}
      </div>
    )
  }

  const agent = agents.agents.find((a) => a.id === session.agentId)
  // Deleting an agent empties its chats' agent (they can't take messages, 409).
  const agentGone =
    session.agentId === null || (!agent && !agents.loading && agents.error === undefined)
  const agentName = agent?.name ?? 'Agent'

  async function submit(content: string) {
    setSendError(undefined)
    follow()
    try {
      await send(content)
      chatSessions.reload()
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === 409) {
        chatSessions.reload()
        agents.reload()
      }
      setSendError(failure)
      throw failure
    }
  }

  function stop() {
    setStopping(true)
    setSendError(undefined)
    stopChatSession(sessionId).then(
      (found) => {
        if (!found) setAbandoned(newest?.id)
      },
      (failure: unknown) => {
        setStopping(false)
        setSendError(failure)
      },
    )
  }

  /** Sends the question again as a new message (ADR 060); a failure shows like any send. */
  function retry(run: string, question: string) {
    if (retried.current === run) return
    retried.current = run
    submit(question).catch(() => {
      retried.current = undefined
    })
  }

  const question = agentGone ? null : retryQuestion(history)

  return (
    <>
      <header className="border-b border-line bg-surface">
        <div className={cx(COLUMN, 'flex items-start gap-1 pt-6 pb-5')}>
          <BackToList className="-mt-2 -ml-3" />
          <div className="min-w-0 flex-1">
            <h1 className="m-0 text-xl font-semibold break-words">{chatTitle(session)}</h1>
            <p className="m-0 mt-0.5 text-sm text-muted">
              {agent ? (
                <>
                  {agent.name} on <span className="font-mono text-xs">{agent.model}</span>
                </>
              ) : agentGone ? (
                'No agent'
              ) : (
                '…'
              )}
            </p>
          </div>
        </div>
      </header>
      {stream.status === 'stopped' && (
        <div role="alert" className="border-b border-line bg-danger-wash text-sm text-danger">
          <div className={cx(COLUMN, 'flex flex-wrap items-center justify-between gap-3 py-2.5')}>
            <span>
              Live replies stopped.
              {stream.problem === undefined
                ? ' Messages you send are still saved.'
                : ` ${errorMessage(stream.problem)}`}
            </span>
            <Button
              size="small"
              onClick={() => {
                stream.reconnect()
              }}
            >
              Try again
            </Button>
          </div>
        </div>
      )}
      {agentGone && (
        <div role="status" className="border-b border-line bg-surface text-sm text-ink">
          <div className={cx(COLUMN, 'flex flex-wrap items-center justify-between gap-3 py-2.5')}>
            <span>Its agent was deleted. You can read this chat but not continue it.</span>
            <Link to="/chat/new" className="font-semibold text-ink underline">
              New chat
            </Link>
          </div>
        </div>
      )}

      <section aria-label="Messages" className={cx(COLUMN, 'flex flex-1 flex-col gap-3 py-9')}>
        {error !== undefined && (
          <div role="alert" className="flex flex-col items-start gap-2 text-muted">
            <p className="m-0">Couldn't load the messages. {errorMessage(error)}</p>
            <button type="button" onClick={reload} className="cursor-pointer text-ink underline">
              Try again
            </button>
          </div>
        )}
        {loading && messages.length === 0 && <p className="m-0 text-muted">Loading messages…</p>}
        {!loading && error === undefined && messages.length === 0 && (
          <p className="m-0 text-muted">No messages yet.</p>
        )}
        {history.map((item, i) =>
          item.kind === 'user' ? (
            // The user's message as a prompt line on a band, not a bubble (ADR 059).
            <div
              key={item.message.id}
              className="flex gap-2.5 rounded-ui border border-line bg-surface px-3 py-2.5 not-first:mt-5"
            >
              <span aria-hidden="true" className="font-mono text-sm text-muted">
                &gt;
              </span>
              <p className="m-0 min-w-0 break-words whitespace-pre-wrap">{item.message.content}</p>
            </div>
          ) : (
            <AgentRun
              key={item.id}
              agentName={agentName}
              steps={item.steps}
              answers={item.answers}
              ms={item.ms}
              working={working && i === history.length - 1}
              since={since}
              onRetry={
                question !== null && i === history.length - 1
                  ? () => retry(item.id, question)
                  : undefined
              }
            />
          ),
        )}
        {/* Nothing from the agent yet: its trace starts with the waiting row. */}
        {working && history.at(-1)?.kind === 'user' && (
          <AgentRun agentName={agentName} steps={[]} answers={[]} working since={since} />
        )}
      </section>

      {/* Stays on screen while the steps above it are read (F3). */}
      <div className={cx(COLUMN, 'sticky bottom-0 z-10 flex flex-col gap-3 bg-ground pt-2 pb-9')}>
        {sendError !== undefined && <Alert type="error" showIcon title={errorMessage(sendError)} />}
        {stream.status === 'replaced' ? (
          // Replies would only reach the other tab, so nothing is sent from here (V4 C4 C).
          <div
            role="status"
            className="flex flex-wrap items-center justify-between gap-3 rounded-ui border border-dashed border-field bg-surface px-4 py-3.5 text-sm"
          >
            <span>
              <strong>This chat is open in another tab.</strong> Continue there, or use it here
              instead.
            </span>
            <Button type="primary" onClick={stream.reconnect}>
              Use it here
            </Button>
          </div>
        ) : (
          <Composer
            disabled={agentGone}
            placeholder={agentGone ? "This chat can't continue" : `Ask ${agentName}`}
            onSend={submit}
            onStop={working ? stop : undefined}
            stopping={stopping}
          />
        )}
      </div>
    </>
  )
}

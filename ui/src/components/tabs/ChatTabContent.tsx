import { SearchOutlined } from '@ant-design/icons'
import { Button, Input } from 'antd'
import { useMemo, useState } from 'react'
import { Link, matchPath, useLocation, useNavigate } from 'react-router'
import type { ChatSession } from '../../api/api'
import { path } from '../../api/http'
import { useAgents } from '../../hooks/useAgents'
import { useChatSessions } from '../../hooks/useChatSessions'
import { useUndoableDelete } from '../../hooks/useUndoableDelete'
import { cx } from '../../utils/cx'
import { formatListTime } from '../../utils/time'
import DeleteFeedback from '../DeleteFeedback'
import Expandable from '../Expandable'
import RenameField from '../RenameField'
import RowActions from '../RowActions'
import { TITLE_LENGTH, chatTitle } from '../chat/messages'
import TabHeading from './TabHeading'
import LoadError from '../LoadError'

/**
 * The chats, most recently active first (ADR 018): title, agent, and time (V1 element 5 A). A
 * search button filters them by title and agent name (V4 C7 B); each one is renamed in its row and
 * deleted with Undo (V4 C6 C).
 */
export default function ChatTabContent() {
  const { chatSessions, loading, error, reload, update, remove } = useChatSessions()
  const { agents } = useAgents()
  const navigate = useNavigate()
  const openId = matchPath('/chat/:chatSessionId', useLocation().pathname)?.params.chatSessionId
  const deletes = useUndoableDelete(remove)
  const [searching, setSearching] = useState(false)
  const [query, setQuery] = useState('')

  const agentNames = useMemo(() => new Map(agents.map((a) => [a.id, a.name])), [agents])
  const agentName = (session: ChatSession) =>
    session.agentId ? (agentNames.get(session.agentId) ?? 'Unknown agent') : 'No agent'

  const needle = query.trim().toLowerCase()
  const matches = (session: ChatSession) =>
    !needle || `${chatTitle(session)} ${agentName(session)}`.toLowerCase().includes(needle)
  const visible = chatSessions.filter((session) => !deletes.hidden.has(session.id))
  const shown = visible.filter(matches)
  // A deleted chat stays in the list while its row folds away (ADR 061).
  const listed = chatSessions.filter(matches)

  // A new chat from an open one keeps its agent: the first agent could be one with other tools.
  const openAgentId = chatSessions.find((session) => session.id === openId)?.agentId
  const newChat = openAgentId ? path`/chat/new?agent=${openAgentId}` : '/chat/new'

  function closeSearch() {
    setSearching(false)
    setQuery('')
  }

  return (
    <section aria-label="Chats" className="flex flex-col gap-4">
      <TabHeading
        title="Chats"
        action={
          <div className="flex gap-1.5">
            <Button
              aria-label="Search chats"
              aria-expanded={searching}
              icon={<SearchOutlined />}
              onClick={() => (searching ? closeSearch() : setSearching(true))}
            />
            <Button type="primary" onClick={() => navigate(newChat)}>
              New chat
            </Button>
          </div>
        }
      />
      {searching && (
        <div className="flex flex-col gap-1.5">
          <Input
            autoFocus
            allowClear
            variant="underlined"
            aria-label="Search chats"
            placeholder="Title or agent"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') closeSearch()
            }}
          />
          {needle && (
            <p role="status" className="m-0 text-xs text-muted">
              {shown.length} of {visible.length} chats
            </p>
          )}
        </div>
      )}
      {error !== undefined && <LoadError what="chats" error={error} onRetry={reload} />}
      {loading && chatSessions.length === 0 && <p className="m-0 text-muted">Loading chats…</p>}
      {!loading && error === undefined && visible.length === 0 && (
        <p className="m-0 text-muted">No chats yet.</p>
      )}
      {listed.length > 0 && (
        <ul className="m-0 list-none p-0">
          {listed.map((session) => (
            <ChatItem
              key={session.id}
              session={session}
              leaving={deletes.hidden.has(session.id)}
              agentName={agentName(session)}
              open={session.id === openId}
              onRename={(title) => update(session.id, { title }).then(() => undefined)}
              onDelete={(returnTo) => {
                deletes.confirm({ id: session.id, name: chatTitle(session), returnTo })
                if (session.id === openId) navigate('/chat/new')
              }}
            />
          ))}
        </ul>
      )}
      <DeleteFeedback deletes={deletes} />
    </section>
  )
}

function ChatItem({
  session,
  leaving,
  agentName,
  open,
  onRename,
  onDelete,
}: {
  session: ChatSession
  /** Deleted, waiting for Undo: the row folds away. */
  leaving: boolean
  agentName: string
  open: boolean
  onRename: (title: string) => Promise<void>
  onDelete: (returnTo: HTMLElement | null) => void
}) {
  const [editing, setEditing] = useState(false)
  // Back from renaming, focus returns to the row's ⋯, not to nowhere.
  const [renamed, setRenamed] = useState(false)
  const title = chatTitle(session)

  return (
    <Expandable
      as="li"
      open={!leaving}
      motion="fold"
      className={cx(
        'border-b border-hairline py-3 pl-3 transition active:scale-98',
        open ? 'bg-ground' : 'hover:bg-ground',
      )}
    >
      {editing ? (
        <RenameField
          label={`Title of ${title}`}
          value={session.title ?? ''}
          maxLength={TITLE_LENGTH}
          onSave={onRename}
          onClose={() => {
            setEditing(false)
            setRenamed(true)
          }}
        />
      ) : (
        <div className="flex items-center gap-1.5">
          <Link
            to={path`/chat/${session.id}`}
            aria-current={open ? 'page' : undefined}
            className="min-w-0 flex-1 text-ink no-underline hover:underline"
          >
            <span className={cx('block break-words', open && 'font-semibold')}>{title}</span>
            <span className="block text-xs text-muted">
              {agentName}, {formatListTime(session.updatedAt)}
            </span>
          </Link>
          <RowActions
            name={title}
            deleteDescription="Its messages are deleted too."
            onRename={() => setEditing(true)}
            focusOnMount={renamed}
            onDelete={onDelete}
          />
        </div>
      )}
    </Expandable>
  )
}

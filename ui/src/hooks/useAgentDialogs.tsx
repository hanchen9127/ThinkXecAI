import { type ReactNode, useState } from 'react'
import { matchPath, useLocation, useNavigate } from 'react-router'
import type { Agent } from '../api/api'
import { path } from '../api/http'
import { toCreateRequest, toUpdateRequest } from '../components/agents/agentForm'
import AgentFormModal from '../components/modals/AgentFormModal'
import DeleteAgentModal from '../components/modals/DeleteAgentModal'
import { useAgents } from './useAgents'
import { useChatSessions } from './useChatSessions'
import { useReturnFocus } from './useReturnFocus'

type Open = { kind: 'create' } | { kind: 'edit'; agent: Agent } | { kind: 'delete'; agent: Agent }

/**
 * The create, edit, and delete dialogs for agents, opened from the list and from a profile.
 * Render `dialogs` once where the hook is used.
 */
export function useAgentDialogs(): {
  openCreate: () => void
  openEdit: (agent: Agent) => void
  openDelete: (agent: Agent) => void
  dialogs: ReactNode
} {
  const [open, setOpen] = useState<Open | null>(null)
  const focus = useReturnFocus(open !== null)
  const show = (next: Open) => {
    focus.remember()
    setOpen(next)
  }
  const { create, update, remove } = useAgents()
  const chatSessions = useChatSessions()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const close = () => setOpen(null)

  let dialogs: ReactNode = null
  if (open?.kind === 'create') {
    dialogs = (
      <AgentFormModal
        onClose={close}
        onSave={async (draft) => {
          const agent = await create(toCreateRequest(draft))
          close()
          navigate(path`/agent/${agent.id}`)
        }}
      />
    )
  } else if (open?.kind === 'edit') {
    const { agent } = open
    dialogs = (
      <AgentFormModal
        agent={agent}
        onClose={close}
        onSave={async (draft) => {
          const request = toUpdateRequest(agent, draft)
          if (Object.keys(request).length > 0) await update(agent.id, request)
          close()
        }}
      />
    )
  } else if (open?.kind === 'delete') {
    const { agent } = open
    dialogs = (
      <DeleteAgentModal
        agent={agent}
        onClose={close}
        onDelete={async () => {
          await remove(agent.id)
          // Its chats now have no agent; the chat list shows that.
          chatSessions.reload()
          close()
          if (matchPath('/agent/:agentId', pathname)?.params.agentId === agent.id)
            navigate('/agent')
        }}
      />
    )
  }

  return {
    openCreate: () => show({ kind: 'create' }),
    openEdit: (agent) => show({ kind: 'edit', agent }),
    openDelete: (agent) => show({ kind: 'delete', agent }),
    dialogs,
  }
}

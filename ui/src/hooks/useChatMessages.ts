import { useCallback, useMemo } from 'react'
import { createChatMessage, listChatMessagesBySession } from '../api/api'
import { useRemoteData } from './useRemoteData'

/**
 * One chat's messages, oldest first, and sending a user message to it (which starts an agent run).
 * Messages the agent produces arrive over SSE (Phase 13 commit 9); until then a reload shows them.
 */
export function useChatMessages(sessionId: string) {
  const load = useMemo(() => () => listChatMessagesBySession(sessionId), [sessionId])
  const { data, loading, error, reload } = useRemoteData(load)

  const send = useCallback(
    async (content: string) => {
      const message = await createChatMessage({ sessionId, content })
      reload()
      return message
    },
    [sessionId, reload],
  )

  return { messages: data ?? [], loading, error, reload, send }
}

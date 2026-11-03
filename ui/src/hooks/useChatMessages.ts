import { useCallback, useMemo, useState } from 'react'
import { type ChatMessage, createChatMessage, listChatMessagesBySession } from '../api/api'
import { useRemoteData } from './useRemoteData'

/**
 * One chat's messages and sending a user message to it (which starts an agent run). Messages the
 * agent pushes over SSE are added with `receive`; a message that also comes with a reload of the
 * history appears once (by id, ISSUE-007).
 */
export function useChatMessages(sessionId: string) {
  const load = useMemo(() => () => listChatMessagesBySession(sessionId), [sessionId])
  const { data, loading, error, reload } = useRemoteData(load)
  const [received, setReceived] = useState<ChatMessage[]>([])

  const messages = useMemo(() => {
    const byId = new Map<string, ChatMessage>()
    for (const message of [...(data ?? []), ...received]) {
      if (!byId.has(message.id)) byId.set(message.id, message)
    }
    return [...byId.values()]
  }, [data, received])

  const receive = useCallback((message: ChatMessage) => {
    setReceived((current) =>
      current.some((m) => m.id === message.id) ? current : [...current, message],
    )
  }, [])

  const send = useCallback(
    async (content: string) => {
      const message = await createChatMessage({ sessionId, content })
      receive(message)
      return message
    },
    [sessionId, receive],
  )

  return { messages, loading, error, reload, send, receive }
}

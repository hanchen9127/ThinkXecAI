import { type ReactNode, useCallback, useMemo } from 'react'
import {
  type CreateChatSessionRequest,
  type UpdateChatSessionRequest,
  createChatSession,
  deleteChatSession,
  listChatSessions,
  updateChatSession,
} from '../api/api'
import { useRemoteData } from '../hooks/useRemoteData'
import { ChatSessionsContext } from './chatSessionsContext'

/** One list of chat sessions shared by the side panel and the chat view. */
export default function ChatSessionsProvider({ children }: { children: ReactNode }) {
  const { data, loading, error, reload } = useRemoteData(listChatSessions)

  const create = useCallback(
    async (request: CreateChatSessionRequest) => {
      const chatSession = await createChatSession(request)
      reload()
      return chatSession
    },
    [reload],
  )
  const update = useCallback(
    async (chatSessionId: string, request: UpdateChatSessionRequest) => {
      const chatSession = await updateChatSession(chatSessionId, request)
      reload()
      return chatSession
    },
    [reload],
  )
  const remove = useCallback(
    async (chatSessionId: string, options?: { keepalive?: boolean }) => {
      await deleteChatSession(chatSessionId, options)
      reload()
    },
    [reload],
  )

  const value = useMemo(
    () => ({ chatSessions: data ?? [], loading, error, reload, create, update, remove }),
    [data, loading, error, reload, create, update, remove],
  )
  return <ChatSessionsContext.Provider value={value}>{children}</ChatSessionsContext.Provider>
}

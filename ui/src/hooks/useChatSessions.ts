import { useContext } from 'react'
import { ChatSessionsContext, type ChatSessionsValue } from '../contexts/chatSessionsContext'

/** The shared chat-session list; only inside `ChatSessionsProvider`. */
export function useChatSessions(): ChatSessionsValue {
  const value = useContext(ChatSessionsContext)
  if (!value) throw new Error('useChatSessions must be used inside ChatSessionsProvider')
  return value
}

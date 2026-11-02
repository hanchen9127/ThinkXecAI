import { createContext } from 'react'
import type { ChatSession, CreateChatSessionRequest, UpdateChatSessionRequest } from '../api/api'

export interface ChatSessionsValue {
  /** Most recently active first (ADR 018). */
  chatSessions: ChatSession[]
  loading: boolean
  error: unknown
  reload: () => void
  create: (request: CreateChatSessionRequest) => Promise<ChatSession>
  update: (chatSessionId: string, request: UpdateChatSessionRequest) => Promise<ChatSession>
  remove: (chatSessionId: string, options?: { keepalive?: boolean }) => Promise<void>
}

export const ChatSessionsContext = createContext<ChatSessionsValue | null>(null)

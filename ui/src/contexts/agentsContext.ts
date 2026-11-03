import { createContext } from 'react'
import type { Agent, CreateAgentRequest, UpdateAgentRequest } from '../api/api'

export interface AgentsValue {
  /** Newest first. */
  agents: Agent[]
  loading: boolean
  error: unknown
  reload: () => void
  create: (request: CreateAgentRequest) => Promise<Agent>
  update: (agentId: string, request: UpdateAgentRequest) => Promise<Agent>
  remove: (agentId: string) => Promise<void>
}

export const AgentsContext = createContext<AgentsValue | null>(null)

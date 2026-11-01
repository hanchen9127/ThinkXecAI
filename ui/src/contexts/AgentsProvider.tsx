import type { ReactNode } from 'react'
import { useAgentsState } from '../hooks/useAgents'
import { AgentsContext } from './agentsContext'

/** One agent list shared by the side panel and the agent views (there is no single-agent endpoint). */
export default function AgentsProvider({ children }: { children: ReactNode }) {
  return <AgentsContext.Provider value={useAgentsState()}>{children}</AgentsContext.Provider>
}

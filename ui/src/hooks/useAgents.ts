import { useCallback, useContext, useMemo } from 'react'
import {
  type CreateAgentRequest,
  type UpdateAgentRequest,
  createAgent,
  deleteAgent,
  listAgents,
  updateAgent,
} from '../api/api'
import { AgentsContext, type AgentsValue } from '../contexts/agentsContext'
import { useRemoteData } from './useRemoteData'

/** Loads the agents (newest first) and their changes; each change reloads the list. For `AgentsProvider`. */
export function useAgentsState(): AgentsValue {
  const { data, loading, error, reload } = useRemoteData(listAgents)

  const create = useCallback(
    async (request: CreateAgentRequest) => {
      const agent = await createAgent(request)
      reload()
      return agent
    },
    [reload],
  )
  const update = useCallback(
    async (agentId: string, request: UpdateAgentRequest) => {
      const agent = await updateAgent(agentId, request)
      reload()
      return agent
    },
    [reload],
  )
  const remove = useCallback(
    async (agentId: string) => {
      await deleteAgent(agentId)
      reload()
    },
    [reload],
  )

  return useMemo(
    () => ({ agents: data ?? [], loading, error, reload, create, update, remove }),
    [data, loading, error, reload, create, update, remove],
  )
}

/** The shared agent list; only inside `AgentsProvider`. */
export function useAgents(): AgentsValue {
  const value = useContext(AgentsContext)
  if (!value) throw new Error('useAgents must be used inside AgentsProvider')
  return value
}

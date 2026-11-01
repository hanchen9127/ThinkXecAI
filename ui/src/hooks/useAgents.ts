import { useCallback } from 'react'
import {
  type CreateAgentRequest,
  type UpdateAgentRequest,
  createAgent,
  deleteAgent,
  listAgents,
  updateAgent,
} from '../api/api'
import { useRemoteData } from './useRemoteData'

/** The agents, newest first, and the changes to them; each change reloads the list. */
export function useAgents() {
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

  return { agents: data ?? [], loading, error, reload, create, update, remove }
}
